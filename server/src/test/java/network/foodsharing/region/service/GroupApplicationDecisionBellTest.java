package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Working-group applications (G3): the decision reaches the applicant as bell + mail naming the group (accepted or
 * declined template), and the admins' bells about the application are removed.
 */
class GroupApplicationDecisionBellTest {

    private final RegionRepository regions = mock(RegionRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final RegionNotificationListener listener = new RegionNotificationListener(mock(MembershipService.class),
            regions, mock(RegionAdminRepository.class), notifications, mock(UserService.class));

    private final Region group = new Region();
    private final UUID applicant = UUID.randomUUID();

    {
        group.setId(UUID.randomUUID());
        group.setName("Öffentlichkeitsarbeit Zürich");
    }

    private NotificationRequest decided(boolean accepted) {
        when(regions.findById(group.getId())).thenReturn(Optional.of(group));
        listener.onApplicationDecided(new GroupService.ApplicationDecided(group.getId(), UUID.randomUUID(), applicant,
                accepted, "Willkommen!"));
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        verify(notifications).removeBells(anyString());
        return request.getValue();
    }

    @Test
    void anAcceptedApplicantGetsBellAndMailWithTheGroupName() {
        NotificationRequest request = decided(true);
        assertThat(request.type()).isEqualTo(NotificationType.GROUP_APPLICATION_ACCEPTED);
        assertThat(request.recipientIds()).containsExactly(applicant);
        assertThat(request.mailTemplate()).isEqualTo("region/group-application-accepted");
        assertThat(request.params()).containsEntry("groupName", "Öffentlichkeitsarbeit Zürich")
                .containsEntry("message", "Willkommen!");
    }

    @Test
    void aDeclinedApplicantGetsTheDeclinedMail() {
        NotificationRequest request = decided(false);
        assertThat(request.type()).isEqualTo(NotificationType.GROUP_APPLICATION_DECLINED);
        assertThat(request.mailTemplate()).isEqualTo("region/group-application-declined");
    }

    @Test
    void aDeletedGroupRingsNobody() {
        when(regions.findById(group.getId())).thenReturn(Optional.empty());
        listener.onApplicationDecided(new GroupService.ApplicationDecided(group.getId(), UUID.randomUUID(), applicant,
                true, null));
        verify(notifications, never()).notify(any());
    }
}
