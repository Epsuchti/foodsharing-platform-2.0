package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.CancellationStatus;
import network.foodsharing.automation.domain.SignoutAlertChannel;
import network.foodsharing.automation.domain.SignoutAlertSetting;
import network.foodsharing.automation.domain.SignoutCancellation;
import network.foodsharing.automation.repository.SignoutAlertSettingRepository;
import network.foodsharing.automation.repository.SignoutCancellationRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.notification.telegram.service.TelegramChatService;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.service.PickupFormat;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Sign-out alerts (AU5): a user signing themself out of a pickup within the cutoff is recorded as an OPEN
 * cancellation (latest sign-out wins) and the managers are alerted right away; once a day (after the configured local
 * time) passed OPEN cancellations get their outcome (REJOINED / NOT_REPLACED / REPLACED, UNKNOWN when the pickup is
 * gone) and NOT_REPLACED is reported to the managers.
 */
@Service
public class SignoutAlertService {

    static final String ACTOR = "signout-alert";
    static final int MAX_HOURS = 336;

    public record SettingView(boolean enabled, boolean cutoffEnabled, int maximumHoursBeforePickup,
                              Set<SignoutAlertChannel> channels, List<String> telegramChatIds, Instant activeSince) {
    }

    public record SettingInput(boolean enabled, boolean cutoffEnabled, int maximumHoursBeforePickup,
                               Set<SignoutAlertChannel> channels, List<String> telegramChatIds) {
    }

    private final SignoutAlertSettingRepository settings;
    private final SignoutCancellationRepository cancellations;
    private final PickupPolicyService pickupPolicies;
    private final PickupScheduleService schedule;
    private final StoreService stores;
    private final UserService users;
    private final NotificationService notifications;
    private final TelegramChatService telegram;
    private final HistoryService history;
    private final AppProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;

    public SignoutAlertService(SignoutAlertSettingRepository settings, SignoutCancellationRepository cancellations,
                               PickupPolicyService pickupPolicies, PickupScheduleService schedule, StoreService stores,
                               UserService users, NotificationService notifications, TelegramChatService telegram,
                               HistoryService history, AppProperties properties, ObjectMapper json, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.cancellations = cancellations;
        this.pickupPolicies = pickupPolicies;
        this.schedule = schedule;
        this.stores = stores;
        this.users = users;
        this.notifications = notifications;
        this.telegram = telegram;
        this.history = history;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Setting of the store (no permission check; callers check mayUseAutomations). */
    @Transactional(readOnly = true)
    public Optional<SettingView> find(UUID storeId) {
        return settings.findByStoreId(storeId).map(this::view);
    }

    /** @throws ApiException 403, 404 (store or POLICY_NOT_FOUND) */
    @Transactional(readOnly = true)
    public SettingView get(UUID storeId) {
        pickupPolicies.requireAutomationStore(storeId);
        return find(storeId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.POLICY_NOT_FOUND));
    }

    /** @throws ApiException 400 VALIDATION_FAILED (args.field), 403, 404 */
    @Transactional
    public SettingView put(UUID storeId, SettingInput input) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        if (input.maximumHoursBeforePickup() < 1 || input.maximumHoursBeforePickup() > MAX_HOURS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field",
                    "maximumHoursBeforePickup");
        }
        Set<SignoutAlertChannel> channels = input.channels() == null ? Set.of() : input.channels();
        if (input.enabled() && channels.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "channels");
        }
        List<String> chatIds = input.telegramChatIds() == null ? List.of() : input.telegramChatIds().stream()
                .filter(s -> s != null && !s.isBlank()).map(String::strip).distinct().toList();
        if (chatIds.size() > 10) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "telegramChatIds");
        }
        SignoutAlertSetting setting = settings.findByStoreId(storeId).orElseGet(SignoutAlertSetting::new);
        boolean wasEnabled = setting.getId() != null && setting.isEnabled();
        setting.setStoreId(storeId);
        if (input.enabled() && !wasEnabled) {
            setting.setActiveSince(clock.instant());
        }
        setting.setEnabled(input.enabled());
        setting.setCutoffEnabled(input.cutoffEnabled());
        setting.setMaximumHoursBeforePickup(input.maximumHoursBeforePickup());
        setting.setChannels(channels.stream().sorted().map(Enum::name).collect(Collectors.joining(",")));
        setting.setTelegramChatIds(chatIds.isEmpty() ? null : json.writeValueAsString(chatIds));
        setting = settings.saveAndFlush(setting);
        history.record(history.event(AutomationAction.SIGNOUT_ALERT_UPDATED, setting.getId())
                .region(store.getRegionId()).metadata("storeId", storeId.toString())
                .metadata("enabled", input.enabled()).build());
        return view(setting);
    }

    /**
     * Cancellations of the store by pickup day (default: the last 30 days up to 30 days ahead).
     *
     * @throws ApiException 400 VALIDATION_FAILED, 403, 404
     */
    @Transactional(readOnly = true)
    public List<SignoutCancellation> list(UUID storeId, LocalDate from, LocalDate to) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        ZoneId zone = schedule.zoneOf(store);
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        LocalDate fromDay = from == null ? today.minusDays(30) : from;
        LocalDate toDay = to == null ? today.plusDays(30) : to;
        if (fromDay.isAfter(toDay)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "from");
        }
        return cancellations.findByStoreIdAndPickupAtGreaterThanEqualAndPickupAtLessThanOrderByPickupAtDescIdDesc(storeId,
                fromDay.atStartOfDay(zone).toInstant(), toDay.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** A24 trigger: a user signed themself out (decision 33: after commit, own transaction). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSignedOut(PickupEvents.PickupSignedOutEvent event) {
        if (!properties.getAutomation().isEnabled() || event.byUserId() == null
                || !event.byUserId().equals(event.userId())) {
            return;
        }
        tx.executeWithoutResult(status -> recordCancellation(event));
    }

    /** The canceller signed up again for the same pickup. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSignedUp(PickupEvents.PickupSignedUpEvent event) {
        tx.executeWithoutResult(status -> cancellations.findByStoreIdAndPickupAtAndUserId(event.storeId(),
                event.pickupAt(), event.userId()).filter(c -> c.getStatus() == CancellationStatus.OPEN)
                .ifPresent(c -> c.setCancellerRejoined(true)));
    }

    /** @return whether a cancellation was recorded */
    boolean recordCancellation(PickupEvents.PickupSignedOutEvent event) {
        Optional<SignoutAlertSetting> found = settings.findByStoreId(event.storeId())
                .filter(SignoutAlertSetting::isEnabled);
        if (found.isEmpty()) {
            return false;
        }
        SignoutAlertSetting setting = found.get();
        if (!event.pickupAt().isAfter(event.at()) || setting.getActiveSince() != null
                && event.pickupAt().isBefore(setting.getActiveSince())) {
            return false;
        }
        if (setting.isCutoffEnabled() && Duration.between(event.at(), event.pickupAt())
                .compareTo(Duration.ofHours(setting.getMaximumHoursBeforePickup())) > 0) {
            return false;
        }
        Optional<Store> store = stores.liveStores(List.of(event.storeId())).stream().findFirst();
        if (store.isEmpty()) {
            return false;
        }
        Optional<PickupSchedule.Slot> slot = schedule.find(store.get(), event.pickupAt());
        SignoutCancellation cancellation = cancellations.findByStoreIdAndPickupAtAndUserId(event.storeId(),
                event.pickupAt(), event.userId()).orElseGet(SignoutCancellation::new);
        cancellation.setStoreId(event.storeId());
        cancellation.setRegionId(store.get().getRegionId());
        cancellation.setPickupAt(event.pickupAt());
        cancellation.setUserId(event.userId());
        cancellation.setCancelledAt(event.at());
        cancellation.setTotalSlots(slot.map(PickupSchedule.Slot::slotCount).orElse(null));
        cancellation.setOccupiedSlots(slot.map(PickupSchedule.Slot::occupied).orElse(null));
        cancellation.setCancellerRejoined(false);
        cancellation.setStatus(CancellationStatus.OPEN);
        cancellation.setFinalCheckedAt(null);
        cancellation.setFinalNotifiedAt(null);
        cancellation.setDryRun(properties.getAutomation().isDryRun());
        String userName = users.findActive(event.userId()).map(User::getDisplayName).orElse("");
        long hours = Duration.between(event.at(), event.pickupAt()).toHours();
        String when = PickupFormat.when(event.pickupAt(), schedule.zoneOf(store.get()));
        String text = userName + " hat sich " + hours + " h vor der Abholung bei " + store.get().getName() + " am "
                + when + " ausgetragen. Belegt: " + cancellation.getOccupiedSlots() + "/"
                + cancellation.getTotalSlots() + ".";
        cancellation.setMessage(text);
        if (!cancellation.isDryRun()) {
            alert(store.get(), setting, NotificationType.PICKUP_SIGNOUT_ALERT, null, event.pickupAt(), when, userName,
                    text, hours, cancellation);
            cancellation.setImmediateNotifiedAt(clock.instant());
        }
        cancellation = cancellations.save(cancellation);
        history.record(history.event(AutomationAction.SIGNOUT_CANCELLATION_RECORDED, cancellation.getId())
                .actorAutomation(ACTOR).secondarySubject(event.userId()).region(store.get().getRegionId())
                .metadata("storeId", event.storeId().toString()).metadata("pickupAt", event.pickupAt().toString())
                .build());
        return true;
    }

    /** Daily check (hourly job, acts once per store and day after the configured local time). */
    public int runDaily() {
        Instant now = clock.instant();
        int finished = 0;
        LocalTime after = LocalTime.parse(properties.getAutomation().getSignoutDailyTime());
        for (SignoutAlertSetting setting : settings.findByEnabledTrue()) {
            Optional<Store> store = stores.liveStores(List.of(setting.getStoreId())).stream().findFirst();
            if (store.isEmpty()) {
                continue;
            }
            ZoneId zone = schedule.zoneOf(store.get());
            ZonedDateTime local = now.atZone(zone);
            if (local.toLocalTime().isBefore(after) || setting.getLastCheckedAt() != null
                    && !setting.getLastCheckedAt().atZone(zone).toLocalDate().isBefore(local.toLocalDate())) {
                continue;
            }
            Integer done = tx.execute(status -> checkStore(store.get(), setting.getId(), now));
            finished += done == null ? 0 : done;
        }
        return finished;
    }

    int checkStore(Store store, UUID settingId, Instant now) {
        SignoutAlertSetting setting = settings.findById(settingId).orElseThrow();
        int finished = 0;
        String storeName = store.getName();
        for (SignoutCancellation c : cancellations.findByStoreIdAndStatusAndPickupAtLessThanEqual(store.getId(),
                CancellationStatus.OPEN, now)) {
            Optional<PickupSchedule.Slot> slot = schedule.find(store, c.getPickupAt());
            c.setStatus(outcome(slot.orElse(null), c.getUserId()));
            c.setFinalCheckedAt(now);
            if (slot.isPresent()) {
                c.setTotalSlots(slot.get().slotCount());
                c.setOccupiedSlots(slot.get().occupied());
            }
            if (c.getStatus() == CancellationStatus.NOT_REPLACED && !c.isDryRun()) {
                String userName = users.findActive(c.getUserId()).map(User::getDisplayName).orElse("");
                String when = PickupFormat.when(c.getPickupAt(), schedule.zoneOf(store));
                long hours = Duration.between(c.getCancelledAt(), c.getPickupAt()).toHours();
                int free = c.getTotalSlots() == null ? 0 : c.getTotalSlots() - c.getOccupiedSlots();
                String text = "Abholung bei " + storeName + " am " + when + ": " + userName + " hat sich " + hours
                        + " h vorher ausgetragen, niemand hat übernommen. Frei: " + free + "/" + c.getTotalSlots()
                        + ".";
                alert(store, setting, NotificationType.PICKUP_SIGNOUT_NOT_REPLACED,
                        "notification/PICKUP_SIGNOUT_NOT_REPLACED", c.getPickupAt(), when, userName, text, hours, c);
                c.setFinalNotifiedAt(now);
                history.record(history.event(AutomationAction.SIGNOUT_FINAL_NOTIFIED, c.getId())
                        .actorAutomation(ACTOR).secondarySubject(c.getUserId()).region(store.getRegionId())
                        .metadata("storeId", store.getId().toString()).metadata("pickupAt", c.getPickupAt().toString())
                        .build());
            }
            finished++;
        }
        setting.setLastCheckedAt(now);
        return finished;
    }

    /** Outcome of a passed cancellation from the sign-ups at pickup time. */
    static CancellationStatus outcome(PickupSchedule.Slot slot, UUID cancellerId) {
        if (slot == null) {
            return CancellationStatus.UNKNOWN;
        }
        if (slot.hasSignup(cancellerId)) {
            return CancellationStatus.REJOINED;
        }
        return slot.slotCount() - slot.occupied() > 0 ? CancellationStatus.NOT_REPLACED : CancellationStatus.REPLACED;
    }

    private void alert(Store store, SignoutAlertSetting setting, NotificationType type, String mail, Instant pickupAt,
                       String when, String userName, String text, long hours, SignoutCancellation cancellation) {
        Set<SignoutAlertChannel> channels = channels(setting.getChannels());
        List<UUID> managers = stores.managerIds(store.getId()).stream()
                .filter(id -> !id.equals(cancellation.getUserId())).toList();
        NotificationRequest.Builder request = NotificationRequest.to(managers, type)
                .param("storeId", store.getId().toString()).param("storeName", store.getName())
                .param("pickupAt", pickupAt.toString()).param("when", when).param("userName", userName)
                .param("hoursBefore", String.valueOf(hours)).param("message", text)
                .link("/stores/" + store.getId() + "/pickups").icon("calendar").senderRegion(store.getRegionId());
        if (channels.contains(SignoutAlertChannel.BELL)) {
            notifications.notify(request.mail(mail).build());
        } else if (channels.contains(SignoutAlertChannel.PUSH)) {
            notifications.pushOnly(request.build());
        }
        if (channels.contains(SignoutAlertChannel.TELEGRAM)) {
            telegram.sendForStore(store.getId(), store.getRegionId(), chatIds(setting.getTelegramChatIds()), text,
                    type.name());
        }
    }

    static Set<SignoutAlertChannel> channels(String csv) {
        Set<SignoutAlertChannel> result = EnumSet.noneOf(SignoutAlertChannel.class);
        if (csv != null && !csv.isBlank()) {
            Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                    .forEach(s -> result.add(SignoutAlertChannel.valueOf(s)));
        }
        return result;
    }

    private List<String> chatIds(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(stored, new TypeReference<List<String>>() { });
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private SettingView view(SignoutAlertSetting s) {
        return new SettingView(s.isEnabled(), s.isCutoffEnabled(), s.getMaximumHoursBeforePickup(),
                channels(s.getChannels()), new ArrayList<>(chatIds(s.getTelegramChatIds())), s.getActiveSince());
    }
}
