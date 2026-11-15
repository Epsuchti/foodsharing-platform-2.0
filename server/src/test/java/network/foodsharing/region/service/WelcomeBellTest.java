package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserNoteService;
import network.foodsharing.user.service.UserService;
import network.foodsharing.verification.service.VerificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * EA8 / N3 (plan §11.4): the welcome team's bell for a new region member. It names the member, links to her profile
 * and carries the identifier {@code REGION_NEW_MEMBER-<userId>}, by which the verification removes it again (the
 * welcome task is done). Who receives it: MembershipServiceTest; storing and removing bells: NotificationServiceTest.
 */
class WelcomeBellTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final UserService users = mock(UserService.class);

    @Test
    void aNewMemberRingsTheWelcomeTeamWithHerIdentifier() {
        MembershipService memberships = mock(MembershipService.class);
        RegionRepository regions = mock(RegionRepository.class);
        Region zurich = new Region();
        zurich.setId(UUID.randomUUID());
        zurich.setName("Zürich Stadt");
        User lea = TestUsers.user("lea@test.local", Role.FOODSHARER);
        lea.setId(UUID.randomUUID());
        UUID welcome = UUID.randomUUID();
        when(memberships.welcomeRecipients(zurich.getId(), lea.getId())).thenReturn(Set.of(welcome));
        when(regions.findById(zurich.getId())).thenReturn(Optional.of(zurich));
        when(users.findActive(lea.getId())).thenReturn(Optional.of(lea));

        new RegionNotificationListener(memberships, regions, mock(RegionAdminRepository.class), notifications, users)
                .onMemberJoined(new MembershipService.RegionMemberJoined(zurich.getId(), lea.getId()));

        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.REGION_NEW_MEMBER);
        assertThat(request.getValue().recipientIds()).containsExactly(welcome);
        assertThat(request.getValue().identifier()).isEqualTo("REGION_NEW_MEMBER-" + lea.getId());
        assertThat(request.getValue().link()).isEqualTo("/users/" + lea.getId());
        assertThat(request.getValue().params()).containsEntry("userName", lea.getDisplayName())
                .containsEntry("regionName", "Zürich Stadt");
    }

    @Test
    void noWelcomeTeamNoBell() {
        MembershipService memberships = mock(MembershipService.class);
        RegionRepository regions = mock(RegionRepository.class);
        UUID region = UUID.randomUUID();
        when(memberships.welcomeRecipients(any(), any())).thenReturn(Set.of());
        when(regions.findById(region)).thenReturn(Optional.of(new Region()));
        new RegionNotificationListener(memberships, regions, mock(RegionAdminRepository.class), notifications, users)
                .onMemberJoined(new MembershipService.RegionMemberJoined(region, UUID.randomUUID()));
        verify(notifications, never()).notify(any());
    }

    @Test
    void theVerificationRemovesTheWelcomeBell() {
        User lea = TestUsers.user("lea@test.local", Role.FOODSHARER);
        lea.setId(UUID.randomUUID());
        lea.setIdentityConfirmedAt(Instant.parse("2026-11-01T10:00:00Z"));
        when(users.require(lea.getId())).thenReturn(lea);
        when(users.setVerified(any(), anyBoolean(), any())).thenReturn(lea);
        CurrentActorService currentActor = mock(CurrentActorService.class);
        UUID ambassador = UUID.randomUUID();
        when(currentActor.requirePrincipal()).thenReturn(new SessionPrincipal(ambassador, UUID.randomUUID(), Role.AMBASSADOR, "a@test.local"));
        when(currentActor.requireUserId()).thenReturn(ambassador);

        new VerificationService(users, mock(UserNoteService.class), notifications, currentActor,
                mock(PermissionService.class), mock(HistoryService.class, RETURNS_DEEP_STUBS),
                mock(ApplicationEventPublisher.class), List.of(), Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC))
                .verify(lea.getId(), "Ausweis gesehen, alles in Ordnung.");

        verify(notifications).removeBells("REGION_NEW_MEMBER-" + lea.getId());
    }
}
