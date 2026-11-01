package network.foodsharing.mailbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxMemberRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.Test;

/** Decision 149: who sees the menu item Postfächer (SessionInfo.mailboxes). */
class MailboxAccessServiceTest {

    private final MailboxRepository mailboxes = mock(MailboxRepository.class);
    private final MailboxMemberRepository members = mock(MailboxMemberRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final MailboxAccessService access = new MailboxAccessService(mailboxes, members,
            mock(MailMessageRepository.class), regions, permissions);

    private static SessionPrincipal principal(Role role) {
        return new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), role, "x@test.local");
    }

    private static Mailbox mailbox(MailboxOwnerType type, UUID ownerId) {
        Mailbox mailbox = new Mailbox();
        mailbox.setName("box");
        mailbox.setEmailName("box");
        mailbox.setOwnerType(type);
        mailbox.setOwnerId(ownerId);
        return mailbox;
    }

    @Test
    void orgaSeesMailboxesWithoutAnyQuery() {
        SessionPrincipal orga = principal(Role.ORGA);
        when(permissions.isOrga(orga)).thenReturn(true);

        assertThat(access.showsMailboxes(orga)).isTrue();
        verify(regions, never()).adminRegionIds(any());
        verify(members, never()).mailboxIdsOf(any());
    }

    @Test
    void unitAdminWithStoreManagerRoleMayCreateOne() {
        SessionPrincipal manager = principal(Role.STORE_MANAGER);
        when(regions.adminRegionIds(manager.userId())).thenReturn(Set.of(UUID.randomUUID()));

        assertThat(access.showsMailboxes(manager)).isTrue();
        verify(members, never()).mailboxIdsOf(any());
    }

    @Test
    void unitAdminBelowStoreManagerWithoutMailboxSeesNothing() {
        SessionPrincipal foodsaver = principal(Role.FOODSAVER);
        when(regions.adminRegionIds(foodsaver.userId())).thenReturn(Set.of(UUID.randomUUID()));

        assertThat(access.showsMailboxes(foodsaver)).isFalse();
    }

    @Test
    void memberReaderSeesMailboxes() {
        SessionPrincipal foodsaver = principal(Role.FOODSAVER);
        UUID boxId = UUID.randomUUID();
        when(members.mailboxIdsOf(foodsaver.userId())).thenReturn(List.of(boxId));
        when(mailboxes.findAllById(List.of(boxId)))
                .thenReturn(List.of(mailbox(MailboxOwnerType.REGION, UUID.randomUUID())));

        assertThat(access.showsMailboxes(foodsaver)).isTrue();
    }

    @Test
    void personalMailboxOwnerSeesMailboxes() {
        SessionPrincipal manager = principal(Role.STORE_MANAGER);
        when(mailboxes.findByOwnerTypeAndOwnerId(MailboxOwnerType.USER, manager.userId()))
                .thenReturn(Optional.of(mailbox(MailboxOwnerType.USER, manager.userId())));

        assertThat(access.showsMailboxes(manager)).isTrue();
    }

    @Test
    void plainFoodsaverSeesNoMailboxes() {
        assertThat(access.showsMailboxes(principal(Role.FOODSAVER))).isFalse();
        assertThat(access.showsMailboxes(null)).isFalse();
    }
}
