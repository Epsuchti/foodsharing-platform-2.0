package network.foodsharing.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.GroupMailboxAddress;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.report.domain.MediationRequest;
import network.foodsharing.report.repository.MediationRequestRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * G2 mediation request (rules the old e2e spec asserted, decision 180): the message needs 10 to 4000 characters; the
 * team of the region's MEDIATION group (admins and members, never the requester) gets a MEDIATION_REQUEST bell that
 * links to the requester's profile. Who may ask at all: ReportPermissionMatrixTest (mediationNeedsMembershipAndTheOption).
 */
class MediationServiceTest {

    private final RegionService regions = mock(RegionService.class);
    private final RegionOptionsService options = mock(RegionOptionsService.class);
    private final ReportPermissionService access = mock(ReportPermissionService.class);
    private final MediationRequestRepository requests = mock(MediationRequestRepository.class);
    private final UserService users = mock(UserService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final HistoryService history = mock(HistoryService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<GroupMailboxAddress> mailbox = mock(ObjectProvider.class);
    private final MediationService service = new MediationService(regions, options, access, requests, users, actors,
            notifications, mock(MailOutboxService.class), history, new AppProperties(), mailbox);

    private final Region zurich = new Region();
    private final Region group = new Region();
    private final User sarah = TestUsers.user("foodsaver@local");
    private final UUID admin = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();

    MediationServiceTest() {
        zurich.setId(UUID.randomUUID());
        zurich.setName("Zürich Stadt");
        group.setId(UUID.randomUUID());
        sarah.setId(UUID.randomUUID());
        sarah.setDisplayName("Sarah Müller");
        SessionPrincipal actor = TestUsers.principal(sarah);
        when(regions.require(zurich.getId())).thenReturn(zurich);
        when(actors.requirePrincipal()).thenReturn(actor);
        when(access.isMember(actor, zurich.getId())).thenReturn(true);
        when(options.read(zurich.getId())).thenReturn(new RegionOptionsService.Options(true, true, null, false, 1,
                false, false));
        when(users.requireActive(sarah.getId())).thenReturn(sarah);
        when(access.mediationGroup(zurich.getId())).thenReturn(Optional.of(group));
        when(regions.adminIds(group.getId())).thenReturn(Set.of(admin));
        when(regions.activeMemberIds(group.getId())).thenReturn(List.of(member, sarah.getId()));
        when(requests.save(any())).thenAnswer(call -> {
            MediationRequest saved = call.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(history.event(any(), any())).thenReturn(mock(HistoryEvent.Builder.class, RETURNS_SELF));
    }

    private ApiErrorCode code(String message) {
        try {
            service.request(zurich.getId(), message, null);
        } catch (ApiException e) {
            return e.getCode();
        }
        throw new AssertionError("expected an ApiException");
    }

    @Test
    void theMessageNeedsTenToFourThousandCharacters() {
        assertThat(code("Hilfe")).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code("   Hilfe!   ")).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(null)).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code("x".repeat(4001))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(requests, never()).save(any());
        verify(notifications, never()).notify(any());
    }

    @Test
    void theMediationTeamIsRungWithALinkToTheRequester() {
        service.request(zurich.getId(), "Wir brauchen Hilfe bei den Abholzeiten.", null);

        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().type()).isEqualTo(NotificationType.MEDIATION_REQUEST);
        assertThat(bell.getValue().recipientIds()).containsExactlyInAnyOrder(admin, member);
        assertThat(bell.getValue().link()).isEqualTo("/profile/" + sarah.getId());
        assertThat(bell.getValue().params()).containsEntry("name", "Sarah Müller");
    }
}
