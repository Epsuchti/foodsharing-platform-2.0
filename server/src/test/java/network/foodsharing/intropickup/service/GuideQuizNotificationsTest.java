package network.foodsharing.intropickup.service;

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
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Q4 (decision 168, was GuideQuizApiIT): a pass of the EinAb guide quiz rings the ambassadors of the person's home
 * region (GUIDE_QUIZ_PASSED, never the person themself, linking to the release list); other quizzes and people without
 * a home region ring nobody.
 */
class GuideQuizNotificationsTest {

    static final UUID HOME = UUID.randomUUID();

    final NotificationService notifications = mock(NotificationService.class);
    final RegionReadService regions = mock(RegionReadService.class);
    final UserService users = mock(UserService.class);
    final GuideQuizNotifications listener = new GuideQuizNotifications(notifications, regions, users);
    final User sarah = user("Sarah Müller", HOME);
    final User bot = user("Reto Baumann", HOME);

    static User user(String name, UUID home) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        user.setHomeRegionId(home);
        return user;
    }

    @BeforeEach
    void setUp() {
        when(users.findActive(sarah.getId())).thenReturn(Optional.of(sarah));
    }

    @Test
    void aPassRingsTheAmbassadorsOfTheHomeRegionButNotThePersonThemself() {
        // an ambassador who passes is not told about themself
        when(regions.ambassadorsOf(HOME)).thenReturn(List.of(bot, sarah));
        listener.onQuizPassed(new QuizSessionService.QuizPassed(sarah.getId(), QuizKind.EINAB_GUIDE));
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.GUIDE_QUIZ_PASSED);
        assertThat(request.getValue().recipientIds()).isEqualTo(Set.of(bot.getId()));
        assertThat(request.getValue().params()).containsEntry("userName", "Sarah Müller");
        assertThat(request.getValue().link()).isEqualTo("/regions/" + HOME + "/intro-pickups/admin?tab=guides");
    }

    @Test
    void otherQuizzesAndPeopleWithoutAHomeRegionRingNobody() {
        when(regions.ambassadorsOf(HOME)).thenReturn(List.of(bot));
        listener.onQuizPassed(new QuizSessionService.QuizPassed(sarah.getId(), QuizKind.FOODSAVER));
        sarah.setHomeRegionId(null);
        listener.onQuizPassed(new QuizSessionService.QuizPassed(sarah.getId(), QuizKind.EINAB_GUIDE));
        verify(notifications, never()).notify(any());
    }
}
