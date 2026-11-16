package network.foodsharing.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.messaging.domain.Conversation;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.domain.RegionConversation;
import network.foodsharing.messaging.repository.RegionConversationRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A33: the region chat holds the active members while the region's conversation is switched on; leaving and the
 * switch resync it, switching it off empties it (messages stay), and the hourly conversation-sync job repairs missed
 * events of store and region chats.
 */
class RegionConversationSyncTest {

    private final RegionConversationRepository links = mock(RegionConversationRepository.class);
    private final MessagingService messaging = mock(MessagingService.class);
    private final MembershipService memberships = mock(MembershipService.class);
    private final RegionService regions = mock(RegionService.class);
    private final RegionConversationSync sync = new RegionConversationSync(links, messaging, memberships, regions);

    private final UUID group = UUID.randomUUID();
    private final UUID chat = UUID.randomUUID();
    private final UUID anna = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();
    private final Region region = new Region();

    @BeforeEach
    void setUp() {
        region.setId(group);
        region.setConversationEnabled(true);
        when(regions.find(group)).thenReturn(Optional.of(region));
        when(regions.require(group)).thenReturn(region);
        RegionConversation link = new RegionConversation();
        link.setRegionId(group);
        link.setConversationId(chat);
        when(links.findByRegionId(group)).thenReturn(Optional.of(link));
        when(links.findAll()).thenReturn(List.of(link));
        when(memberships.membersOf(group)).thenReturn(List.of(anna, ben));
    }

    @Test
    void leavingResyncsTheChatToTheRemainingMembers() {
        when(memberships.membersOf(group)).thenReturn(List.of(anna));
        sync.on(new MembershipService.RegionMemberLeft(group, ben));
        verify(messaging).syncMembers(chat, Set.of(anna));
    }

    @Test
    void switchingOffEmptiesTheChatAndSwitchingOnRefillsIt() {
        region.setConversationEnabled(false);
        sync.syncRegion(group);
        verify(messaging).syncMembers(chat, Set.of());

        region.setConversationEnabled(true);
        sync.syncRegion(group);
        verify(messaging).syncMembers(chat, Set.of(anna, ben));
    }

    @Test
    void anEnabledRegionWithoutChatGetsALockedRegionConversation() {
        when(links.findByRegionId(group)).thenReturn(Optional.empty());
        Conversation created = new Conversation();
        created.setId(UUID.randomUUID());
        when(messaging.createLocked(ConversationKind.REGION)).thenReturn(created);

        assertThat(sync.ensure(group)).contains(created.getId());
        verify(links).save(any(RegionConversation.class));
        verify(messaging).syncMembers(created.getId(), Set.of(anna, ben));
    }

    @Test
    void aSwitchedOffRegionWithoutChatGetsNone() {
        region.setConversationEnabled(false);
        when(links.findByRegionId(group)).thenReturn(Optional.empty());
        assertThat(sync.syncRegion(group)).isZero();
        assertThat(sync.ensure(group)).isEmpty();
        verify(messaging, never()).createLocked(any());
    }

    @Test
    void theHourlyJobRepairsStoreAndRegionChats() {
        when(messaging.syncMembers(eq(chat), any())).thenReturn(1);
        StoreConversationSync stores = mock(StoreConversationSync.class);
        when(stores.syncAll()).thenReturn(2);
        ConversationSyncJob job = new ConversationSyncJob(mock(JobRunner.class), stores, sync);

        job.runNow();

        verify(stores).syncAll();
        verify(messaging).syncMembers(chat, Set.of(anna, ben));
        assertThat(job.jobName()).isEqualTo("conversation-sync");
    }
}
