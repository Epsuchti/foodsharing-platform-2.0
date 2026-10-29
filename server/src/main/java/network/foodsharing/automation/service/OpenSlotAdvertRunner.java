package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import network.foodsharing.automation.domain.AdvertChannel;
import network.foodsharing.automation.domain.AdvertStatus;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.OpenSlotAdvert;
import network.foodsharing.automation.domain.OpenSlotAdvertAudit;
import network.foodsharing.automation.repository.OpenSlotAdvertAuditRepository;
import network.foodsharing.automation.repository.OpenSlotAdvertRepository;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.notification.telegram.service.TelegramChatService;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.TeamChatPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Open-slot advert run (AU4, port of the reference {@code runAdvertisementAutomations}). Per enabled advert and
 * future pickup with {@code pickupAt - triggerHours <= now < pickupAt}: a filled pickup marks an earlier SENT advert
 * as filled (cleared again when the slot is freed: only fills that last count) or records OCCUPIED_AT_TRIGGER with
 * the first sign-up; a pickup freed after OCCUPIED_AT_TRIGGER gets the late-cancellation handling with the settings
 * of the store's lowest advert number; otherwise one advert per (advert, pickup, trigger hours) with a random message
 * per channel. Each advert runs in its own transaction.
 */
@Component
public class OpenSlotAdvertRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenSlotAdvertRunner.class);
    static final String ACTOR = "open-slot-advert";
    static final Set<AdvertStatus> SENT_STATUSES = Set.of(AdvertStatus.SENT,
            AdvertStatus.LATE_CANCELLATION_ADVERTISEMENT_SENT,
            AdvertStatus.LATE_CANCELLATION_REMINDER_AND_ADVERTISEMENT_SENT);
    static final Set<AdvertStatus> REMINDER_STATUSES = Set.of(AdvertStatus.LATE_CANCELLATION_REMINDER_SENT,
            AdvertStatus.LATE_CANCELLATION_REMINDER_AND_ADVERTISEMENT_SENT);
    static final Set<AdvertStatus> LATE_ADVERT_STATUSES = Set.of(AdvertStatus.LATE_CANCELLATION_ADVERTISEMENT_SENT,
            AdvertStatus.LATE_CANCELLATION_REMINDER_AND_ADVERTISEMENT_SENT);

    private final OpenSlotAdvertRepository adverts;
    private final OpenSlotAdvertAuditRepository audits;
    private final OpenSlotAdvertService service;
    private final PickupScheduleService schedule;
    private final StoreService stores;
    private final NotificationService notifications;
    private final TelegramChatService telegram;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TeamChatPort teamChat;

    public OpenSlotAdvertRunner(OpenSlotAdvertRepository adverts, OpenSlotAdvertAuditRepository audits,
                                OpenSlotAdvertService service, PickupScheduleService schedule, StoreService stores,
                                NotificationService notifications, TelegramChatService telegram,
                                HistoryService history, AppProperties properties, Clock clock,
                                PlatformTransactionManager transactionManager, TeamChatPort teamChat) {
        this.adverts = adverts;
        this.audits = audits;
        this.service = service;
        this.schedule = schedule;
        this.stores = stores;
        this.notifications = notifications;
        this.telegram = telegram;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.teamChat = teamChat;
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** All enabled adverts (job). */
    public PickupPolicyRunner.RunResult runAll() {
        return run(adverts.findByEnabledTrueOrderByStoreIdAscAdvertNumberAsc(), false);
    }

    /** "Run now" for one store; {@code dryRun} forces a dry run. */
    public PickupPolicyRunner.RunResult runStore(UUID storeId, boolean dryRun) {
        return run(adverts.findByStoreIdOrderByAdvertNumber(storeId).stream().filter(OpenSlotAdvert::isEnabled)
                .toList(), dryRun);
    }

    private final class Counters {
        int evaluated;
        int acted;
        int skipped;
        int failed;
        int sent;
        final Set<String> lateMessageSent = new HashSet<>();
        final Set<String> lateAdvertSent = new HashSet<>();
        final List<String> messages = new ArrayList<>();
    }

    PickupPolicyRunner.RunResult run(List<OpenSlotAdvert> enabled, boolean forceDryRun) {
        if (!properties.getAutomation().isEnabled()) {
            return new PickupPolicyRunner.RunResult(0, 0, 0, 0, 0, 0, true,
                    List.of("Open-slot adverts skipped: automation is disabled."));
        }
        boolean globalDryRun = forceDryRun || properties.getAutomation().isDryRun();
        Instant now = clock.instant();
        Counters counters = new Counters();
        Map<UUID, List<OpenSlotAdvert>> byStore = enabled.stream().collect(Collectors.groupingBy(
                OpenSlotAdvert::getStoreId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<UUID, List<OpenSlotAdvert>> entry : byStore.entrySet()) {
            Optional<Store> store = stores.liveStores(List.of(entry.getKey())).stream().findFirst();
            if (store.isEmpty()) {
                continue;
            }
            List<OpenSlotAdvert> all = adverts.findByStoreIdOrderByAdvertNumber(entry.getKey());
            OpenSlotAdvert settings = all.getFirst();
            List<OpenSlotAdvert> storeEnabled = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(OpenSlotAdvert::getAdvertNumber)).toList();
            int maxTrigger = storeEnabled.stream().mapToInt(OpenSlotAdvert::getTriggerHoursBefore).max().orElse(0);
            List<PickupSchedule.Slot> slots = schedule.schedule(store.get(), now,
                    now.plus(Duration.ofHours(maxTrigger)).plusMillis(1));
            for (OpenSlotAdvert advert : storeEnabled) {
                try {
                    tx.executeWithoutResult(status -> runAdvert(store.get(), advert, settings, storeEnabled, slots,
                            globalDryRun || advert.isDryRun(), now, counters));
                } catch (RuntimeException ex) {
                    counters.failed++;
                    counters.messages.add("Advert " + advert.getAdvertNumber() + " of " + store.get().getName()
                            + " failed: " + ex.getMessage());
                    log.warn("open-slot advert {} failed", advert.getId(), ex);
                }
            }
        }
        counters.messages.addFirst((globalDryRun ? "Would send " : "Processed ") + counters.acted + " message(s) after "
                + "checking " + counters.evaluated + " pickup(s).");
        return new PickupPolicyRunner.RunResult(counters.evaluated, counters.acted, 0, counters.skipped, 0,
                counters.failed, globalDryRun, counters.messages);
    }

    private void runAdvert(Store store, OpenSlotAdvert advert, OpenSlotAdvert settings, List<OpenSlotAdvert> enabled,
                           List<PickupSchedule.Slot> slots, boolean dryRun, Instant now, Counters counters) {
        OpenSlotAdvertService.AdvertView view = service.view(advert);
        ZoneId zone = schedule.zoneOf(store);
        int checked = 0;
        int acted = 0;
        if (view.channels().isEmpty() || missingMessages(view)) {
            counters.skipped++;
            if (dryRun) {
                summary(store, advert, now, "Advert skipped: no channel or missing messages for a selected channel.");
            }
            return;
        }
        int max = Math.max(0, properties.getAutomation().getMaxAdvertisementsPerRun());
        for (PickupSchedule.Slot slot : slots) {
            Instant pickupAt = slot.pickupAt();
            Instant triggerAt = pickupAt.minus(Duration.ofHours(advert.getTriggerHoursBefore()));
            if (triggerAt.isAfter(now) || !pickupAt.isAfter(now)) {
                continue;
            }
            counters.evaluated++;
            checked++;
            Optional<OpenSlotAdvertAudit> existing = audits
                    .findForPickup(advert.getId(),
                            pickupAt, advert.getTriggerHoursBefore(), AdvertStatus.DRY_RUN_SUMMARY)
                    .stream().findFirst();
            if (slot.occupied() >= slot.slotCount()) {
                if (!dryRun) {
                    existing.filter(a -> a.getFilledAt() == null && SENT_STATUSES.contains(a.getStatus()))
                            .ifPresent(a -> a.setFilledAt(now));
                    if (existing.isEmpty()) {
                        UUID first = slot.signups().stream().min(Comparator.comparing(PickupSignup::getCreatedAt))
                                .map(PickupSignup::getUserId).orElse(null);
                        OpenSlotAdvertAudit audit = audit(store, advert, pickupAt, AdvertStatus.OCCUPIED_AT_TRIGGER,
                                false);
                        audit.setUserId(first);
                        audit.setReason("Pickup was occupied when this advert became due.");
                        audits.save(audit);
                    }
                    deleteTelegram(store.getId(), pickupAt, null, now);
                }
                counters.skipped++;
                continue;
            }
            int free = slot.slotCount() - slot.occupied();
            AdvertTemplates.Values values = new AdvertTemplates.Values(pickupAt, zone, store.getName(), free,
                    properties.getBaseUrl() + "/stores/" + store.getId() + "/pickups");
            if (existing.isPresent()) {
                OpenSlotAdvertAudit audit = existing.get();
                if (!dryRun && audit.getFilledAt() != null) {
                    audit.setFilledAt(null);
                }
                if (audit.getStatus() == AdvertStatus.OCCUPIED_AT_TRIGGER) {
                    acted += lateCancellation(store, advert, settings, enabled, audit, values, dryRun, now, max,
                            counters);
                } else {
                    counters.skipped++;
                    // "Run now" feedback: one advert per pickup and trigger, so a re-run after a channel change
                    // sends nothing new (it looked like a lost Telegram message, coordinator report M7)
                    counters.messages.add("Advert " + advert.getAdvertNumber() + " of " + store.getName()
                            + ": already sent for " + values.pickupAt() + " (one message per pickup).");
                }
                continue;
            }
            if (counters.sent >= max) {
                counters.skipped++;
                break;
            }
            Sent sent = send(store, view, values, dryRun);
            if (!dryRun) {
                OpenSlotAdvertAudit audit = audit(store, advert, pickupAt, AdvertStatus.SENT, false);
                audit.setReason("Advert sent.");
                audit.setMessage(sent.text());
                audit.setSentAt(now);
                audit.setTelegramChatId(sent.telegramChatId());
                audit.setTelegramMessageId(sent.telegramMessageId());
                audit = audits.save(audit);
                if (sent.telegramMessageId() != null) {
                    deleteTelegram(store.getId(), pickupAt, audit.getId(), now);
                }
                history.record(history.event(AutomationAction.ADVERT_SENT, advert.getId()).actorAutomation(ACTOR)
                        .region(store.getRegionId()).metadata("storeId", store.getId().toString())
                        .metadata("pickupAt", pickupAt.toString()).metadata("advertNumber", advert.getAdvertNumber())
                        .build());
            }
            acted++;
            counters.acted++;
            counters.sent++;
        }
        if (dryRun) {
            summary(store, advert, now, "Checked " + checked + " pickup(s), would send " + acted + " message(s).");
        }
    }

    private int lateCancellation(Store store, OpenSlotAdvert advert, OpenSlotAdvert settings,
                                 List<OpenSlotAdvert> enabled, OpenSlotAdvertAudit audit,
                                 AdvertTemplates.Values values, boolean dryRun, Instant now, int max,
                                 Counters counters) {
        Instant pickupAt = values.pickupAt();
        String key = store.getId() + ":" + pickupAt;
        boolean sendMessage = settings.isSendLateCancellationMessage()
                && !pickupAt.isAfter(now.plus(Duration.ofHours(settings.getLateCancellationMaxHours())))
                && audit.getUserId() != null && !counters.lateMessageSent.contains(key)
                && !audits.existsByStoreIdAndPickupAtAndStatusIn(store.getId(), pickupAt, REMINDER_STATUSES);
        boolean sendAdvert = settings.isResendLatestAdvertAfterLateCancellation()
                && isLatestDue(advert, enabled, pickupAt, now) && counters.sent < max
                && !counters.lateAdvertSent.contains(key)
                && !audits.existsByStoreIdAndPickupAtAndStatusIn(store.getId(), pickupAt, LATE_ADVERT_STATUSES);
        if (!sendMessage && !sendAdvert) {
            if (!dryRun) {
                audit.setStatus(AdvertStatus.LATE_CANCELLATION_NO_ACTION);
                audit.setReason("No late-cancellation action is enabled or applicable for this pickup.");
            }
            counters.skipped++;
            return 0;
        }
        String reminder = sendMessage ? AdvertTemplates.render(settings.getLateCancellationMessage(), values) : null;
        if (!dryRun) {
            if (sendMessage) {
                notifications.notify(NotificationRequest.to(List.of(audit.getUserId()),
                                NotificationType.PICKUP_LATE_CANCELLATION)
                        .param("storeId", store.getId().toString()).param("storeName", store.getName())
                        .param("pickupAt", pickupAt.toString())
                        .param("when", network.foodsharing.pickup.service.PickupFormat.when(pickupAt, values.zone()))
                        .param("message", reminder).link("/stores/" + store.getId() + "/pickups")
                        .mail("notification/PICKUP_LATE_CANCELLATION").build());
            }
            Sent sent = sendAdvert ? send(store, service.view(advert), values, false) : null;
            audit.setStatus(sendMessage && sendAdvert ? AdvertStatus.LATE_CANCELLATION_REMINDER_AND_ADVERTISEMENT_SENT
                    : sendMessage ? AdvertStatus.LATE_CANCELLATION_REMINDER_SENT
                    : AdvertStatus.LATE_CANCELLATION_ADVERTISEMENT_SENT);
            audit.setReason("Pickup was released after this advert time.");
            audit.setMessage(sendMessage ? reminder : sent.text());
            audit.setSentAt(now);
            if (sent != null && sent.telegramMessageId() != null) {
                audit.setTelegramChatId(sent.telegramChatId());
                audit.setTelegramMessageId(sent.telegramMessageId());
                deleteTelegram(store.getId(), pickupAt, audit.getId(), now);
            }
            history.record(history.event(AutomationAction.LATE_CANCELLATION_HANDLED, advert.getId())
                    .actorAutomation(ACTOR).region(store.getRegionId()).secondarySubject(audit.getUserId())
                    .metadata("storeId", store.getId().toString()).metadata("pickupAt", pickupAt.toString())
                    .metadata("status", audit.getStatus().name()).build());
        }
        if (sendMessage) {
            counters.lateMessageSent.add(key);
        }
        if (sendAdvert) {
            counters.lateAdvertSent.add(key);
            counters.sent++;
        }
        int acted = (sendMessage ? 1 : 0) + (sendAdvert ? 1 : 0);
        counters.acted += acted;
        return acted;
    }

    /** The enabled advert of the store whose trigger time is the latest one already due (ties: highest number). */
    static boolean isLatestDue(OpenSlotAdvert advert, List<OpenSlotAdvert> enabled, Instant pickupAt, Instant now) {
        return enabled.stream()
                .filter(c -> !pickupAt.minus(Duration.ofHours(c.getTriggerHoursBefore())).isAfter(now))
                .max(Comparator.comparing((OpenSlotAdvert c) -> pickupAt.minus(Duration.ofHours(
                        c.getTriggerHoursBefore()))).thenComparingInt(OpenSlotAdvert::getAdvertNumber))
                .map(c -> c.getId().equals(advert.getId())).orElse(false);
    }

    record Sent(String text, String telegramChatId, Long telegramMessageId) {
    }

    private Sent send(Store store, OpenSlotAdvertService.AdvertView view, AdvertTemplates.Values values,
                      boolean dryRun) {
        List<String> texts = new ArrayList<>();
        String chatId = null;
        Long messageId = null;
        String identifier = "PICKUP_OPEN_SLOT-" + store.getId() + "-" + values.pickupAt();
        List<UUID> recipients = view.channels().contains(AdvertChannel.PUSH)
                ? stores.memberAndJumperIds(store.getId()) : List.of();
        if (view.channels().contains(AdvertChannel.STORE_CHAT)) {
            // M5 (A33): a system message in the team and jumper chats replaces the grouped M4 bell (decision 61).
            String text = AdvertTemplates.render(random(view.storeMessages()), values);
            texts.add(text);
            if (!dryRun) {
                teamChat.postToTeamChat(store.getId(), TeamChatPort.StoreChat.TEAM, text);
                teamChat.postToTeamChat(store.getId(), TeamChatPort.StoreChat.JUMPER, text);
            }
        }
        if (view.channels().contains(AdvertChannel.PUSH)) {
            String text = AdvertTemplates.render(random(view.pushMessages()), values);
            texts.add(text);
            if (!dryRun) {
                notifications.pushOnly(request(recipients, store, values, text, identifier).build());
            }
        }
        if (view.channels().contains(AdvertChannel.TELEGRAM)) {
            String text = AdvertTemplates.render(random(view.telegramMessages()), values);
            texts.add(text);
            if (!dryRun && view.telegramChatId() != null) {
                List<TelegramChatService.SentMessage> sent = telegram.sendForStore(store.getId(),
                        store.getRegionId(), List.of(view.telegramChatId()), text, "PICKUP_OPEN_SLOT");
                if (!sent.isEmpty()) {
                    chatId = sent.getFirst().chatId();
                    messageId = sent.getFirst().messageId();
                }
            }
        }
        return new Sent(truncate(String.join("\n---\n", texts), 4000), chatId, messageId);
    }

    private static NotificationRequest.Builder request(List<UUID> recipients, Store store,
                                                       AdvertTemplates.Values values, String text, String identifier) {
        return NotificationRequest.to(recipients, NotificationType.PICKUP_OPEN_SLOT)
                .param("storeId", store.getId().toString()).param("storeName", store.getName())
                .param("pickupAt", values.pickupAt().toString())
                .param("when", network.foodsharing.pickup.service.PickupFormat.when(values.pickupAt(), values.zone()))
                .param("freeSlots", String.valueOf(values.freeSlots())).param("message", text)
                .link("/stores/" + store.getId() + "/pickups").identifier(identifier).icon("calendar");
    }

    /** Deletes Telegram adverts of this pickup (except {@code keep}) and of passed pickups of the store. */
    private void deleteTelegram(UUID storeId, Instant pickupAt, UUID keep, Instant now) {
        for (OpenSlotAdvertAudit old : audits.telegramToDelete(storeId, pickupAt, now)) {
            if (old.getId().equals(keep) || old.getTelegramChatId() == null) {
                continue;
            }
            try {
                telegram.deleteMessage(old.getTelegramChatId(), old.getTelegramMessageId());
            } catch (RuntimeException ex) {
                log.info("Telegram advert {} could not be deleted: {}", old.getId(), ex.toString());
            }
            old.setTelegramDeletedAt(now);
        }
    }

    private void summary(Store store, OpenSlotAdvert advert, Instant now, String reason) {
        OpenSlotAdvertAudit audit = audit(store, advert, null, AdvertStatus.DRY_RUN_SUMMARY, true);
        audit.setReason(reason);
        audit.setSentAt(now);
        audits.save(audit);
        history.record(history.event(AutomationAction.DRY_RUN_EXECUTED, advert.getId()).actorAutomation(ACTOR)
                .region(store.getRegionId()).metadata("storeId", store.getId().toString())
                .metadata("advertNumber", advert.getAdvertNumber()).build());
    }

    private static OpenSlotAdvertAudit audit(Store store, OpenSlotAdvert advert, Instant pickupAt, AdvertStatus status,
                                             boolean dryRun) {
        OpenSlotAdvertAudit audit = new OpenSlotAdvertAudit();
        audit.setAdvertId(advert.getId());
        audit.setAdvertNumber(advert.getAdvertNumber());
        audit.setStoreId(store.getId());
        audit.setRegionId(store.getRegionId());
        audit.setPickupAt(pickupAt);
        audit.setTriggerHoursBefore(advert.getTriggerHoursBefore());
        audit.setStatus(status);
        audit.setDryRun(dryRun);
        return audit;
    }

    private boolean missingMessages(OpenSlotAdvertService.AdvertView view) {
        return view.channels().contains(AdvertChannel.STORE_CHAT) && view.storeMessages().isEmpty()
                || view.channels().contains(AdvertChannel.PUSH) && view.pushMessages().isEmpty()
                || view.channels().contains(AdvertChannel.TELEGRAM) && view.telegramMessages().isEmpty();
    }

    private static String random(List<String> messages) {
        return messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
