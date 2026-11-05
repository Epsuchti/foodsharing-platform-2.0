package network.foodsharing.intropickup.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Q4 (decision 168): after a pass of the EINAB_GUIDE quiz the ambassadors of the user's home region get the bell
 * GUIDE_QUIZ_PASSED, linking to the Ready-for-release list of the EinAb admin page. Without a home region nobody is
 * told (the user shows up as a candidate in every region they are a member of).
 */
@Service
public class GuideQuizNotifications {

    private final NotificationService notifications;
    private final RegionReadService regionReads;
    private final UserService users;

    public GuideQuizNotifications(NotificationService notifications, RegionReadService regionReads,
                                  UserService users) {
        this.notifications = notifications;
        this.regionReads = regionReads;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQuizPassed(QuizSessionService.QuizPassed event) {
        if (event.kind() != QuizKind.EINAB_GUIDE) {
            return;
        }
        users.findActive(event.userId()).ifPresent(user -> {
            UUID home = user.getHomeRegionId();
            if (home == null) {
                return;
            }
            List<UUID> admins = regionReads.ambassadorsOf(home).stream().map(User::getId)
                    .filter(id -> !id.equals(user.getId())).toList();
            if (admins.isEmpty()) {
                return;
            }
            notifications.notify(NotificationRequest.to(admins, NotificationType.GUIDE_QUIZ_PASSED)
                    .param("userName", user.getDisplayName())
                    .link("/regions/" + home + "/intro-pickups/admin?tab=guides").icon("graduation-cap").build());
        });
    }
}
