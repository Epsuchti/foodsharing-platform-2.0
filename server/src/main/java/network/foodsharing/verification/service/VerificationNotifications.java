package network.foodsharing.verification.service;

import java.util.List;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.service.UserVerifiedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Verification bells and mails after commit: USER_VERIFIED (identifier {@code USER_VERIFIED-<userId>}, mail
 * {@code verification/verified}), PASSPORT_CREATED (mail {@code verification/passport-created}) and a GENERIC bell
 * (title {@code bell.passportRequested}) to the ambassadors of the home region for a passport request.
 */
@Service
public class VerificationNotifications {

    private final NotificationService notifications;
    private final RegionReadService regionReads;
    private final RegionService regions;
    private final UserService users;

    public VerificationNotifications(NotificationService notifications, RegionReadService regionReads,
                                     RegionService regions, UserService users) {
        this.notifications = notifications;
        this.regionReads = regionReads;
        this.regions = regions;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVerified(UserVerifiedEvent event) {
        notifications.notify(NotificationRequest.to(List.of(event.userId()), NotificationType.USER_VERIFIED)
                .identifier(BellService.identifier(NotificationType.USER_VERIFIED, event.userId()))
                .link("/passport").mail("verification/verified").icon("badge-check").build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPassportsCreated(PassportService.PassportsCreated event) {
        String region = regions.find(event.regionId()).map(Region::getName).orElse("");
        notifications.notify(NotificationRequest.to(event.userIds(), NotificationType.PASSPORT_CREATED)
                .param("regionName", region).link("/passport").mail("verification/passport-created")
                .icon("id-card").build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPassportRequested(PassportService.PassportRequested event) {
        List<java.util.UUID> ambassadors = regionReads.ambassadorsOf(event.regionId()).stream().map(User::getId)
                .toList();
        if (ambassadors.isEmpty()) {
            return;
        }
        String name = users.findActive(event.userId()).map(User::getDisplayName).orElse("");
        notifications.notify(NotificationRequest.to(ambassadors, NotificationType.GENERIC)
                .param("titleKey", "bell.passportRequested").param("userName", name)
                .link("/regions/" + event.regionId() + "/passports").icon("id-card").build());
    }
}
