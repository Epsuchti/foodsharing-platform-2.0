package network.foodsharing.pickup.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupAction;
import network.foodsharing.pickup.domain.PickupDayConversation;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDayConversationRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.DayChatPort;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PU3 day chat (decision 164): one named group chat per store and calendar day (store time zone). Opening it finds or
 * creates the chat and adds the caller and everyone signed up that day who isn't a member yet; nobody is removed.
 */
@Service
public class PickupDayConversationService {

    /** The day chat after opening it. */
    public record Opened(UUID conversationId, String name, boolean created, int addedMembers) {
    }

    private final PickupDayConversationRepository mappings;
    private final PickupSignupRepository signups;
    private final PickupScheduleService schedule;
    private final StoreService stores;
    private final StorePermissionService rights;
    private final DayChatPort chats;
    private final CurrentActorService currentActor;
    private final HistoryService history;

    public PickupDayConversationService(PickupDayConversationRepository mappings, PickupSignupRepository signups,
                                        PickupScheduleService schedule, StoreService stores,
                                        StorePermissionService rights, DayChatPort chats,
                                        CurrentActorService currentActor, HistoryService history) {
        this.mappings = mappings;
        this.signups = signups;
        this.schedule = schedule;
        this.stores = stores;
        this.rights = rights;
        this.chats = chats;
        this.currentActor = currentActor;
        this.history = history;
    }

    /**
     * @param name used only when the chat is created
     * @throws ApiException 404 STORE_NOT_FOUND / STORE_DELETED, 403 ACCESS_DENIED, 400 NO_SIGNUPS / VALIDATION_FAILED
     */
    @Transactional
    public Opened open(UUID storeId, LocalDate date, String name) {
        stores.require(storeId);
        Store store = stores.lock(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        UUID me = actor.userId();
        ZoneId zone = schedule.zoneOf(store);
        List<PickupSignup> daySignups = signups.findInRange(List.of(storeId), date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
        Set<UUID> signedUp = new LinkedHashSet<>();
        daySignups.stream().sorted(java.util.Comparator.comparing(PickupSignup::getPickupAt)
                .thenComparing(PickupSignup::getCreatedAt)).forEach(s -> signedUp.add(s.getUserId()));
        StorePermissionService.require(DayChatRule.mayOpen(rights.teamStatus(me, storeId), signedUp.contains(me)));

        Set<UUID> wanted = new LinkedHashSet<>(signedUp);
        wanted.add(me);
        // the mapping goes with its conversation (FK cascade); the store row lock serialises concurrent creation
        PickupDayConversation mapping = mappings.findByStoreIdAndPickupDate(storeId, date).orElse(null);
        boolean created = mapping == null;
        int added;
        if (created) {
            Set<UUID> others = new LinkedHashSet<>(signedUp);
            others.remove(me);
            if (others.isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.NO_SIGNUPS);
            }
            String trimmed = name == null ? "" : name.strip();
            if (trimmed.isEmpty() || trimmed.length() > 200) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            UUID conversationId = chats.createGroup(me, others, trimmed);
            mapping = new PickupDayConversation();
            mapping.setStoreId(storeId);
            mapping.setPickupDate(date);
            mapping.setConversationId(conversationId);
            mapping = mappings.save(mapping);
            added = wanted.size();
        } else {
            added = chats.addMissingMembers(mapping.getConversationId(), wanted);
        }
        history.record(history.event(PickupAction.DAY_CONVERSATION_OPENED, storeId).region(store.getRegionId())
                .metadata("storeId", storeId.toString()).metadata("date", date.toString())
                .metadata("conversationId", mapping.getConversationId().toString()).metadata("created", created)
                .metadata("addedMembers", added).build());
        return new Opened(mapping.getConversationId(), chats.name(mapping.getConversationId()), created, added);
    }
}
