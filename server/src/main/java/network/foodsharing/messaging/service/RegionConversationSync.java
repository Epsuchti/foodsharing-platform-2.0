package network.foodsharing.messaging.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.domain.RegionConversation;
import network.foodsharing.messaging.repository.RegionConversationRepository;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionSettingsService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region/working group conversation (A33): the active members of a region whose {@code conversation_enabled} is on.
 * Synced on join/leave/removal and when switched; disabling empties the member list (messages are kept).
 */
@Service
public class RegionConversationSync {

    private final RegionConversationRepository links;
    private final MessagingService messaging;
    private final MembershipService memberships;
    private final RegionService regions;

    public RegionConversationSync(RegionConversationRepository links, MessagingService messaging,
                                  MembershipService memberships, RegionService regions) {
        this.links = links;
        this.messaging = messaging;
        this.memberships = memberships;
        this.regions = regions;
    }

    @EventListener
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public void on(MembershipService.RegionMemberJoined event) {
        syncRegion(event.regionId());
    }

    @EventListener
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public void on(MembershipService.RegionMemberLeft event) {
        syncRegion(event.regionId());
    }

    @EventListener
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public void on(RegionMembersService.MemberRemoved event) {
        syncRegion(event.regionId());
    }

    @EventListener
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public void on(RegionSettingsService.RegionConversationToggled event) {
        syncRegion(event.regionId());
    }

    /** The conversation id when enabled (created on first use), else empty. */
    @Transactional
    @NoHistory(reason = "createLocked/syncMembers record their history")
    public Optional<UUID> ensure(UUID regionId) {
        if (!regions.require(regionId).isConversationEnabled()) {
            return Optional.empty();
        }
        Optional<RegionConversation> existing = links.findByRegionId(regionId);
        if (existing.isPresent()) {
            return Optional.of(existing.get().getConversationId());
        }
        RegionConversation link = new RegionConversation();
        link.setRegionId(regionId);
        link.setConversationId(messaging.createLocked(ConversationKind.REGION).getId());
        links.save(link);
        messaging.syncMembers(link.getConversationId(), new HashSet<>(memberships.membersOf(regionId)));
        return Optional.of(link.getConversationId());
    }

    /** Reconciles the conversation of one region (only when it exists or is enabled). */
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public int syncRegion(UUID regionId) {
        boolean enabled = regions.find(regionId).map(r -> r.isConversationEnabled()).orElse(false);
        Optional<UUID> conversationId = enabled ? ensure(regionId)
                : links.findByRegionId(regionId).map(RegionConversation::getConversationId);
        if (conversationId.isEmpty()) {
            return 0;
        }
        Set<UUID> wanted = enabled ? new HashSet<>(memberships.membersOf(regionId)) : Set.of();
        return messaging.syncMembers(conversationId.get(), wanted);
    }

    /** Reconciles every existing region conversation (job). */
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public int syncAll() {
        int changes = 0;
        for (RegionConversation link : links.findAll()) {
            changes += syncRegion(link.getRegionId());
        }
        return changes;
    }
}
