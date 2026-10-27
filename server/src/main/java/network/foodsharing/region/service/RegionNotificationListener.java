package network.foodsharing.region.service;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notifications of the region module, sent after the triggering transaction committed (docs/conventions.md A12).
 */
@Service
public class RegionNotificationListener {

    private final MembershipService memberships;
    private final RegionRepository regions;
    private final RegionAdminRepository admins;
    private final NotificationService notifications;
    private final UserService users;

    public RegionNotificationListener(MembershipService memberships, RegionRepository regions,
                                      RegionAdminRepository admins, NotificationService notifications,
                                      UserService users) {
        this.memberships = memberships;
        this.regions = regions;
        this.admins = admins;
        this.notifications = notifications;
        this.users = users;
    }

    /**
     * Bell {@code REGION_NEW_MEMBER} to the welcome team (identifier {@code REGION_NEW_MEMBER-<userId>}, removed again
     * when the user is verified).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberJoined(MembershipService.RegionMemberJoined event) {
        Set<UUID> recipients = memberships.welcomeRecipients(event.regionId(), event.userId());
        Region region = regions.findById(event.regionId()).orElse(null);
        if (recipients.isEmpty() || region == null) {
            return;
        }
        String userName = users.findActive(event.userId()).map(user -> user.getDisplayName()).orElse("");
        notifications.notify(NotificationRequest.to(recipients, NotificationType.REGION_NEW_MEMBER)
                .param("regionName", region.getName())
                .param("regionId", region.getId().toString())
                .param("userName", userName)
                .param("userId", event.userId().toString())
                .link("/users/" + event.userId())
                .identifier(BellService.identifier(NotificationType.REGION_NEW_MEMBER, event.userId()))
                .icon("user-plus")
                .build());
    }

    /** Bell GROUP_APPLICATION_RECEIVED to the group admins (identifier per group and applicant). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationSubmitted(GroupService.ApplicationSubmitted event) {
        Region group = regions.findById(event.groupId()).orElse(null);
        if (group == null) {
            return;
        }
        notifications.notify(NotificationRequest.to(admins.findUserIds(event.groupId()),
                        NotificationType.GROUP_APPLICATION_RECEIVED)
                .param("groupName", group.getName())
                .param("groupId", group.getId().toString())
                .param("userName", users.findActive(event.userId()).map(user -> user.getDisplayName()).orElse(""))
                .link("/groups/" + group.getId())
                .identifier(applicationIdentifier(event.groupId(), event.userId()))
                .icon("users")
                .build());
    }

    /** Bell + mail to the applicant; the admins' application bells are removed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationDecided(GroupService.ApplicationDecided event) {
        Region group = regions.findById(event.groupId()).orElse(null);
        if (group == null) {
            return;
        }
        notifications.removeBells(applicationIdentifier(event.groupId(), event.userId()));
        notifications.notify(NotificationRequest.to(java.util.List.of(event.userId()), event.accepted()
                        ? NotificationType.GROUP_APPLICATION_ACCEPTED : NotificationType.GROUP_APPLICATION_DECLINED)
                .param("groupName", group.getName())
                .param("message", event.message())
                .link("/groups/" + group.getId())
                .mail(event.accepted() ? "region/group-application-accepted" : "region/group-application-declined")
                .icon("users")
                .build());
    }

    /** The group mail goes to every member (bell + mail) except the sender. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGroupMail(GroupService.GroupMailRequested event) {
        Region group = regions.findById(event.groupId()).orElse(null);
        if (group == null) {
            return;
        }
        Set<UUID> recipients = new java.util.LinkedHashSet<>(memberships.membersOf(event.groupId()));
        recipients.remove(event.senderId());
        notifications.notify(NotificationRequest.to(recipients, NotificationType.GROUP_MAIL)
                .param("groupName", group.getName())
                .param("subject", event.subject())
                .param("body", event.body())
                .param("senderName", users.findActive(event.senderId()).map(user -> user.getDisplayName()).orElse(""))
                .link("/groups/" + group.getId())
                .mail("region/group-mail")
                .icon("mail")
                .build());
    }

    /** GENERIC bell (title {@code bell.regionMemberRemoved}) to a removed member. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberRemoved(RegionMembersService.MemberRemoved event) {
        Region region = regions.findById(event.regionId()).orElse(null);
        if (region == null) {
            return;
        }
        notifications.notify(NotificationRequest.to(java.util.List.of(event.userId()), NotificationType.GENERIC)
                .param("titleKey", "bell.regionMemberRemoved")
                .param("regionName", region.getName())
                .param("reason", event.reason())
                .link("/regions/mine")
                .build());
    }

    public static String applicationIdentifier(UUID groupId, UUID userId) {
        return BellService.identifier(NotificationType.GROUP_APPLICATION_RECEIVED, groupId, userId);
    }
}
