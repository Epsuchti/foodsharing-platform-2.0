package network.foodsharing.automation.service;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AdvertChannel;
import network.foodsharing.automation.domain.AdvertStatus;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.OpenSlotAdvert;
import network.foodsharing.automation.repository.OpenSlotAdvertAuditRepository;
import network.foodsharing.automation.repository.OpenSlotAdvertRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.store.domain.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Open-slot adverts of a store (AU4): CRUD with validation and the planned-adverts preview. */
@Service
public class OpenSlotAdvertService {

    static final int MAX_ADVERTS = 5;
    static final int MAX_HOURS = 168;
    static final String DEFAULT_LATE_CANCELLATION_MESSAGE = "Du hast gerade den Slot {{datetimeDe}} freigegeben. Du "
            + "bist trotzdem verantwortlich für diesen Slot! Bitte sorge für Ersatz und notifiziere sowohl das Team als "
            + "auch den Notfallchat.";

    /** Stored advert with decoded lists. */
    public record AdvertView(UUID id, UUID storeId, int advertNumber, boolean enabled, boolean dryRun,
                             int triggerHoursBefore, Set<AdvertChannel> channels, String telegramChatId,
                             List<String> storeMessages, List<String> pushMessages, List<String> telegramMessages,
                             boolean sendLateCancellationMessage, String lateCancellationMessage,
                             int lateCancellationMaxHours, boolean resendLatestAdvertAfterLateCancellation) {
    }

    public record AdvertInput(boolean enabled, boolean dryRun, int triggerHoursBefore, Set<AdvertChannel> channels,
                              String telegramChatId, List<String> storeMessages, List<String> pushMessages,
                              List<String> telegramMessages, boolean sendLateCancellationMessage,
                              String lateCancellationMessage, int lateCancellationMaxHours,
                              boolean resendLatestAdvertAfterLateCancellation) {
    }

    public record Planned(Instant pickupAt, int advertNumber, Instant triggerAt, Set<AdvertChannel> channels,
                          int freeSlots) {
    }

    private final OpenSlotAdvertRepository adverts;
    private final OpenSlotAdvertAuditRepository audits;
    private final PickupPolicyService pickupPolicies;
    private final PickupScheduleService schedule;
    private final HistoryService history;
    private final ObjectMapper json;
    private final Clock clock;

    public OpenSlotAdvertService(OpenSlotAdvertRepository adverts, OpenSlotAdvertAuditRepository audits,
                                 PickupPolicyService pickupPolicies, PickupScheduleService schedule,
                                 HistoryService history, ObjectMapper json, Clock clock) {
        this.adverts = adverts;
        this.audits = audits;
        this.pickupPolicies = pickupPolicies;
        this.schedule = schedule;
        this.history = history;
        this.json = json;
        this.clock = clock;
    }

    /** All adverts of the store (no permission check; callers check mayUseAutomations). */
    @Transactional(readOnly = true)
    public List<AdvertView> list(UUID storeId) {
        return adverts.findByStoreIdOrderByAdvertNumber(storeId).stream().map(this::view).toList();
    }

    /** @throws ApiException 403, 404 (store or ADVERT_NOT_FOUND) */
    @Transactional(readOnly = true)
    public AdvertView get(UUID storeId, int advertNumber) {
        pickupPolicies.requireAutomationStore(storeId);
        return view(require(storeId, advertNumber));
    }

    /**
     * Creates or replaces advert {@code advertNumber} (1..5).
     *
     * @throws ApiException 400 VALIDATION_FAILED (args.field), ADVERT_MESSAGES_MISSING (args.channel),
     *                      INVALID_TEMPLATE_VARIABLE (args.variable); 403; 404
     */
    @Transactional
    public AdvertView put(UUID storeId, int advertNumber, AdvertInput input) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        validate(advertNumber, input);
        Optional<OpenSlotAdvert> existing = adverts.findByStoreIdAndAdvertNumber(storeId, advertNumber);
        OpenSlotAdvert advert = existing.orElseGet(OpenSlotAdvert::new);
        advert.setStoreId(storeId);
        advert.setAdvertNumber(advertNumber);
        advert.setEnabled(input.enabled());
        advert.setDryRun(input.dryRun());
        advert.setTriggerHoursBefore(input.triggerHoursBefore());
        advert.setChannels(input.channels().stream().sorted().map(Enum::name).collect(Collectors.joining(",")));
        advert.setTelegramChatId(blankToNull(input.telegramChatId()));
        advert.setStoreMessages(write(input.storeMessages()));
        advert.setPushMessages(write(input.pushMessages()));
        advert.setTelegramMessages(write(input.telegramMessages()));
        advert.setSendLateCancellationMessage(input.sendLateCancellationMessage());
        String late = blankToNull(input.lateCancellationMessage());
        advert.setLateCancellationMessage(late == null ? DEFAULT_LATE_CANCELLATION_MESSAGE : late);
        advert.setLateCancellationMaxHours(input.lateCancellationMaxHours());
        advert.setResendLatestAdvertAfterLateCancellation(input.resendLatestAdvertAfterLateCancellation());
        advert = adverts.saveAndFlush(advert);
        history.record(history.event(AutomationAction.ADVERT_UPDATED, advert.getId()).region(store.getRegionId())
                .metadata("storeId", storeId.toString()).metadata("advertNumber", advertNumber)
                .metadata("created", existing.isEmpty()).build());
        return view(advert);
    }

    /** @throws ApiException 403, 404 (store or ADVERT_NOT_FOUND) */
    @Transactional
    public void delete(UUID storeId, int advertNumber) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        OpenSlotAdvert advert = require(storeId, advertNumber);
        adverts.delete(advert);
        history.record(history.event(AutomationAction.ADVERT_DELETED, advert.getId()).region(store.getRegionId())
                .metadata("storeId", storeId.toString()).metadata("advertNumber", advertNumber).build());
    }

    /**
     * Adverts of enabled adverts that will trigger within {@code days} (default 7, 1..30) for pickups with free
     * slots and no advert yet.
     *
     * @throws ApiException 400 VALIDATION_FAILED, 403, 404
     */
    @Transactional(readOnly = true)
    public List<Planned> planned(UUID storeId, Integer days) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        int window = days == null ? 7 : days;
        if (window < 1 || window > 30) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "days");
        }
        List<AdvertView> enabled = list(storeId).stream().filter(AdvertView::enabled).toList();
        if (enabled.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        Instant until = now.plus(Duration.ofDays(window));
        int maxTrigger = enabled.stream().mapToInt(AdvertView::triggerHoursBefore).max().orElse(0);
        List<PickupSchedule.Slot> slots = schedule.schedule(store, now, until.plus(Duration.ofHours(maxTrigger)));
        List<Planned> planned = new ArrayList<>();
        for (AdvertView advert : enabled) {
            for (PickupSchedule.Slot slot : slots) {
                Instant triggerAt = slot.pickupAt().minus(Duration.ofHours(advert.triggerHoursBefore()));
                int free = slot.slotCount() - slot.occupied();
                if (free <= 0 || triggerAt.isAfter(until) || !slot.pickupAt().isAfter(now)
                        || !audits.findForPickup(
                        advert.id(), slot.pickupAt(), advert.triggerHoursBefore(), AdvertStatus.DRY_RUN_SUMMARY)
                        .isEmpty()) {
                    continue;
                }
                planned.add(new Planned(slot.pickupAt(), advert.advertNumber(), triggerAt.isBefore(now) ? now
                        : triggerAt, advert.channels(), free));
            }
        }
        planned.sort(Comparator.comparing(Planned::triggerAt).thenComparing(Planned::advertNumber));
        return planned;
    }

    void validate(int advertNumber, AdvertInput input) {
        if (advertNumber < 1 || advertNumber > MAX_ADVERTS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "advertNumber");
        }
        if (input.triggerHoursBefore() < 1 || input.triggerHoursBefore() > MAX_HOURS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "triggerHoursBefore");
        }
        if (input.lateCancellationMaxHours() < 1 || input.lateCancellationMaxHours() > MAX_HOURS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED)
                    .withArg("field", "lateCancellationMaxHoursBefore");
        }
        Set<AdvertChannel> channels = input.channels() == null ? Set.of() : input.channels();
        if (input.enabled() && channels.isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "channels");
        }
        requireMessages(channels, AdvertChannel.STORE_CHAT, input.storeMessages());
        requireMessages(channels, AdvertChannel.PUSH, input.pushMessages());
        requireMessages(channels, AdvertChannel.TELEGRAM, input.telegramMessages());
        if (channels.contains(AdvertChannel.TELEGRAM) && blankToNull(input.telegramChatId()) == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "telegramChatId");
        }
        if (input.sendLateCancellationMessage() && blankToNull(input.lateCancellationMessage()) == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "lateCancellationMessage");
        }
        List<String> all = new ArrayList<>();
        all.addAll(clean(input.storeMessages()));
        all.addAll(clean(input.pushMessages()));
        all.addAll(clean(input.telegramMessages()));
        all.add(input.lateCancellationMessage());
        AdvertTemplates.unknownVariable(all).ifPresent(variable -> {
            throw ApiException.badRequest(ApiErrorCode.INVALID_TEMPLATE_VARIABLE).withArg("variable", variable);
        });
    }

    private static void requireMessages(Set<AdvertChannel> channels, AdvertChannel channel, List<String> messages) {
        if (channels.contains(channel) && clean(messages).isEmpty()) {
            throw ApiException.badRequest(ApiErrorCode.ADVERT_MESSAGES_MISSING).withArg("channel", channel.name());
        }
    }

    static List<String> clean(List<String> messages) {
        return messages == null ? List.of() : messages.stream().filter(m -> m != null && !m.isBlank())
                .map(String::strip).toList();
    }

    OpenSlotAdvert require(UUID storeId, int advertNumber) {
        return adverts.findByStoreIdAndAdvertNumber(storeId, advertNumber)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.ADVERT_NOT_FOUND));
    }

    AdvertView view(OpenSlotAdvert advert) {
        return new AdvertView(advert.getId(), advert.getStoreId(), advert.getAdvertNumber(), advert.isEnabled(),
                advert.isDryRun(), advert.getTriggerHoursBefore(), channels(advert.getChannels()),
                advert.getTelegramChatId(), read(advert.getStoreMessages()), read(advert.getPushMessages()),
                read(advert.getTelegramMessages()), advert.isSendLateCancellationMessage(),
                advert.getLateCancellationMessage(), advert.getLateCancellationMaxHours(),
                advert.isResendLatestAdvertAfterLateCancellation());
    }

    static Set<AdvertChannel> channels(String csv) {
        Set<AdvertChannel> result = EnumSet.noneOf(AdvertChannel.class);
        if (csv != null && !csv.isBlank()) {
            Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                    .forEach(s -> result.add(AdvertChannel.valueOf(s)));
        }
        return result;
    }

    private String write(List<String> messages) {
        List<String> cleaned = clean(messages);
        return cleaned.isEmpty() ? null : json.writeValueAsString(cleaned);
    }

    List<String> read(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(stored, new TypeReference<List<String>>() { });
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
