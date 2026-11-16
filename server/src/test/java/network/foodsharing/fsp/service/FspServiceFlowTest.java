package network.foodsharing.fsp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspFollower;
import network.foodsharing.fsp.domain.FspFollowerRole;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.domain.FspWallPost;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.fsp.repository.FspWallPostRepository;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * FS1 service paths without a database (formerly FspFlowIT): a wall post rings the bell followers and mails the mail
 * followers but never the author, managers must be active members of the region, and the activation removes the
 * FSP_ACTIVATE bells.
 */
class FspServiceFlowTest {

    static final UUID REGION = UUID.randomUUID();
    static final SessionPrincipal AUTHOR = TestAuth.principal(SeedUsers.GUIDE);
    static final UUID BELL_FOLLOWER = UUID.randomUUID();
    static final UUID MAIL_FOLLOWER = UUID.randomUUID();
    static final FspRules.Facts MANAGER = new FspRules.Facts(false, true, true, true, true, FspStatus.ACTIVE);
    static final FspRules.Facts ACTIVATOR = new FspRules.Facts(true, true, false, false, false, FspStatus.INACTIVE);

    FoodSharePointRepository fsps = mock(FoodSharePointRepository.class);
    FspFollowerRepository followers = mock(FspFollowerRepository.class);
    FspWallPostRepository posts = mock(FspWallPostRepository.class);
    FspPermissionService access = mock(FspPermissionService.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    RegionService regions = mock(RegionService.class);
    NotificationService notifications = mock(NotificationService.class);
    MailOutboxService mails = mock(MailOutboxService.class);
    UserService users = mock(UserService.class);
    HistoryService history = mock(HistoryService.class);
    FspService service = new FspService(fsps, followers, posts, access, actors, regions, mock(GeocodingClient.class),
            mock(UploadService.class), notifications, mails, users, history, mock(AppProperties.class),
            Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC), mock(FeatureToggleService.class),
            mock(FspCleaningRegionService.class));
    FoodSharePoint fsp = new FoodSharePoint();

    @BeforeEach
    void setUp() {
        fsp.setId(UUID.randomUUID());
        fsp.setRegionId(REGION);
        fsp.setName("Fairteiler Seebach");
        fsp.setStatus(FspStatus.ACTIVE);
        when(fsps.findById(fsp.getId())).thenReturn(Optional.of(fsp));
        when(actors.requirePrincipal()).thenReturn(AUTHOR);
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
    }

    static FspFollower follower(UUID user, FspFollowerRole role, boolean bell, boolean mail) {
        FspFollower row = new FspFollower();
        row.setUserId(user);
        row.setRole(role);
        row.setBell(bell);
        row.setMail(mail);
        return row;
    }

    static User user(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    @Test
    void aWallPostRingsAndMailsTheFollowersButNeverTheAuthor() {
        User author = user(AUTHOR.userId(), "guide@local");
        when(actors.requireUser()).thenReturn(author);
        when(access.requireVisible(AUTHOR, fsp)).thenReturn(MANAGER);
        when(posts.save(any())).thenAnswer(call -> {
            FspWallPost post = call.getArgument(0);
            post.setId(UUID.randomUUID());
            return post;
        });
        when(followers.findByFspId(fsp.getId())).thenReturn(List.of(
                follower(AUTHOR.userId(), FspFollowerRole.MANAGER, true, true),
                follower(BELL_FOLLOWER, FspFollowerRole.FOLLOWER, true, false),
                follower(MAIL_FOLLOWER, FspFollowerRole.FOLLOWER, false, true)));
        when(users.findActive(Set.of(MAIL_FOLLOWER))).thenReturn(List.of(user(MAIL_FOLLOWER, "mail@local")));

        service.post(fsp.getId(), "Regal ist voll!");

        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().type()).isEqualTo(NotificationType.FSP_NEW_POST);
        assertThat(bell.getValue().recipientIds()).containsExactly(BELL_FOLLOWER);
        ArgumentCaptor<MailDraft> mail = ArgumentCaptor.forClass(MailDraft.class);
        verify(mails).enqueue(mail.capture());
        assertThat(mail.getAllValues()).singleElement().satisfies(draft -> {
            assertThat(draft.to()).isEqualTo("mail@local");
            assertThat(draft.template()).isEqualTo(FspService.MAIL_NEW_POST);
        });
    }

    @Test
    void managersMustBeActiveMembersOfTheRegion() {
        UUID outsider = UUID.randomUUID();
        when(access.requireVisible(AUTHOR, fsp)).thenReturn(MANAGER);
        when(regions.activeMembersAmong(Set.of(REGION), Set.of(outsider))).thenReturn(Set.of());
        assertThatThrownBy(() -> service.setManagers(fsp.getId(), List.of(outsider)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        verify(followers, never()).save(any());
    }

    @Test
    void activationRemovesTheActivateBellsAndManagersMayNotActivate() {
        fsp.setStatus(FspStatus.INACTIVE);
        when(access.requireVisible(AUTHOR, fsp)).thenReturn(MANAGER);
        assertThatThrownBy(() -> service.setStatus(fsp.getId(), FspStatus.ACTIVE))
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));

        when(access.requireVisible(AUTHOR, fsp)).thenReturn(ACTIVATOR);
        service.setStatus(fsp.getId(), FspStatus.ACTIVE);
        assertThat(fsp.getStatus()).isEqualTo(FspStatus.ACTIVE);
        verify(notifications).removeBells("FSP_ACTIVATE-" + fsp.getId());
    }
}
