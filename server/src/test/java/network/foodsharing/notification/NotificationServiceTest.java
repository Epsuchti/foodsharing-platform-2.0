package network.foodsharing.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.repository.BellRepository;
import network.foodsharing.notification.repository.UserNotificationSettingRepository;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.ChannelDispatcher;
import network.foodsharing.notification.service.MailChannel;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.notification.service.NotificationSettingsService;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.SharedJpaSliceConfig;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** A12: a bell row per active recipient, a queued mail unless bell-only, history, removal by identifier. */
@JpaSliceTest
@Import(SharedJpaSliceConfig.class)
class NotificationServiceTest {

    @Autowired
    private BellService bells;

    @Autowired
    private BellRepository bellRepository;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserService userService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private HistoryEntryRepository history;

    @Autowired
    private UserNotificationSettingRepository settings;

    @Autowired
    private AppProperties properties;

    @Autowired
    private ApplicationContext context;

    private final MailOutboxService mails = mock(MailOutboxService.class);

    /** The real notification pipeline (bells, settings, mail channel) of the shared slice, mails mocked. */
    private NotificationService notifications;

    private User lea;
    private User marco;

    @BeforeEach
    void setUp() {
        MailChannel mailChannel = new MailChannel(mails, properties, context.getBeanProvider(RegionSenderNames.class));
        ChannelDispatcher channels = new ChannelDispatcher(List.of(mailChannel),
                new NotificationSettingsService(settings, historyService));
        notifications = new NotificationService(bells, userService, historyService, channels);
        lea = users.saveAndFlush(TestUsers.user("lea@test.local", Role.FOODSHARER));
        User inactive = TestUsers.user("marco@test.local", Role.FOODSAVER);
        inactive.setActive(false);
        inactive.setPreferredLanguage(Language.IT);
        marco = users.saveAndFlush(inactive);
    }

    @Test
    void bellOnlyNotificationsCreateOneBellPerActiveRecipient() {
        String identifier = BellService.identifier(NotificationType.REGION_NEW_MEMBER, lea.getId());

        List<Bell> created = notifications.notify(NotificationRequest.to(List.of(lea.getId(), marco.getId(),
                        UUID.randomUUID()), NotificationType.REGION_NEW_MEMBER)
                .param("regionName", "Zürich Stadt").link("/users/" + lea.getId()).identifier(identifier).build());

        assertThat(created).singleElement().satisfies(bell -> {
            assertThat(bell.getUserId()).isEqualTo(lea.getId());
            assertThat(bell.getTitleKey()).isEqualTo("bell.REGION_NEW_MEMBER");
            assertThat(bell.getIdentifier()).isEqualTo("REGION_NEW_MEMBER-" + lea.getId());
            assertThat(bell.getMailOutboxId()).isNull();
            assertThat(bells.params(bell)).containsEntry("regionName", "Zürich Stadt");
        });
        verify(mails, never()).enqueue(any());
        assertThat(history.findAll()).extracting(entry -> entry.getAction())
                .contains("NOTIFICATION_SENT", "NOTIFICATION_BELL_CREATED");

        assertThat(notifications.removeBells(identifier)).isEqualTo(1);
        assertThat(bellRepository.findByIdentifierAndDeletedAtIsNull(identifier)).isEmpty();
        assertThat(bells.search(lea.getId(), NotificationType.REGION_NEW_MEMBER)).hasSize(1);
    }

    @Test
    void mailNotificationsAreQueuedInTheRecipientsLanguage() {
        UUID mailId = UUID.randomUUID();
        when(mails.enqueue(any())).thenReturn(mailId);

        notifications.notify(NotificationRequest.to(List.of(lea.getId()), NotificationType.USER_VERIFIED)
                .param("regionName", "Zürich Stadt").link("/onboarding").mail("verification/verified").build());

        ArgumentCaptor<MailDraft> draft = ArgumentCaptor.forClass(MailDraft.class);
        verify(mails).enqueue(draft.capture());
        assertThat(draft.getValue().to()).isEqualTo("lea@test.local");
        assertThat(draft.getValue().template()).isEqualTo("verification/verified");
        assertThat(draft.getValue().language()).isEqualTo(Language.DE);
        assertThat(draft.getValue().variables()).containsEntry("regionName", "Zürich Stadt")
                .containsEntry("link", "http://localhost:4200/onboarding");
        assertThat(bells.search(lea.getId(), null)).singleElement()
                .extracting(Bell::getMailOutboxId).isEqualTo(mailId);
    }

    @Test
    void genericBellsUseTheGivenTitleKey() {
        List<Bell> created = notifications.notify(NotificationRequest.to(List.of(lea.getId()), NotificationType.GENERIC)
                .param("titleKey", "bell.passportRequested").build());
        assertThat(created).singleElement().extracting(Bell::getTitleKey).isEqualTo("bell.passportRequested");
    }
}
