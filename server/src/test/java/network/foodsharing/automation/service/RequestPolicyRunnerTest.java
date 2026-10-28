package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.RequestDecision;
import network.foodsharing.automation.domain.RequestPolicy;
import network.foodsharing.automation.domain.RequestPolicyAudit;
import network.foodsharing.automation.repository.RequestPolicyAuditRepository;
import network.foodsharing.automation.repository.RequestPolicyRepository;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreApplicationReceivedEvent;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.StoreTeamService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.transaction.PlatformTransactionManager;

/** Request policy (AU2): distance rule, missing address, dry run, approvals limit (M4 B4). */
class RequestPolicyRunnerTest {

    static final UUID STORE = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();

    RequestPolicyRepository policies = mock(RequestPolicyRepository.class);
    RequestPolicyAuditRepository audits = mock(RequestPolicyAuditRepository.class);
    StoreService stores = mock(StoreService.class);
    StoreTeamService team = mock(StoreTeamService.class);
    UserService users = mock(UserService.class);
    AppProperties properties = new AppProperties();
    RequestPolicy policy = new RequestPolicy();
    User user = new User();
    RequestPolicyRunner runner;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/automation/messages");
        messages.setDefaultEncoding("UTF-8");
        runner = new RequestPolicyRunner(policies, audits, stores, team, users,
                mock(HistoryService.class, RETURNS_DEEP_STUBS), messages, properties,
                mock(PlatformTransactionManager.class));
        policy.setStoreId(STORE);
        policy.setEnabled(true);
        policy.setDryRun(false);
        policy.setDistanceRuleEnabled(true);
        policy.setMaximumDistanceKm(5);
        Store store = new Store();
        store.setId(STORE);
        store.setRegionId(UUID.randomUUID());
        store.setLatitude(47.3779);
        store.setLongitude(8.5403);
        when(stores.require(STORE)).thenReturn(store);
        user.setId(USER);
        user.setLatitude(47.3800);
        user.setLongitude(8.5400);
        when(users.require(USER)).thenReturn(user);
        when(policies.findByStoreId(STORE)).thenReturn(Optional.of(policy));
        when(policies.findByEnabledTrue()).thenReturn(List.of(policy));
        when(team.applicantIds(STORE)).thenReturn(List.of(USER));
        when(team.acceptByAutomation(any(), any(), anyString())).thenReturn(true);
        when(team.declineByAutomation(any(), any(), anyString(), anyString())).thenReturn(true);
    }

    RequestPolicyAudit savedAudit() {
        ArgumentCaptor<RequestPolicyAudit> captor = ArgumentCaptor.forClass(RequestPolicyAudit.class);
        verify(audits).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void nearApplicantIsAcceptedOnApplication() {
        runner.onApplication(new StoreApplicationReceivedEvent(STORE, USER));
        verify(team).acceptByAutomation(STORE, USER, RequestPolicyRunner.ACTOR);
        assertThat(savedAudit().getStatus()).isEqualTo(RequestDecision.APPROVED);
    }

    @Test
    void farApplicantIsDeclinedWithTheReferenceText() {
        user.setLatitude(46.9490);
        user.setLongitude(7.4392);
        runner.onApplication(new StoreApplicationReceivedEvent(STORE, USER));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(team).declineByAutomation(eq(STORE), eq(USER), eq(RequestPolicyRunner.ACTOR), message.capture());
        assertThat(message.getValue()).startsWith("Die Anfrage wurde automatisch abgelehnt, weil die Entfernung von 9")
                .endsWith("das Maximum von 5.0 km überschreitet.");
        RequestPolicyAudit audit = savedAudit();
        assertThat(audit.getStatus()).isEqualTo(RequestDecision.DECLINED);
        assertThat(audit.getReason()).isEqualTo("DISTANCE");
    }

    @Test
    void missingCoordinatesSkip() {
        user.setLatitude(null);
        runner.onApplication(new StoreApplicationReceivedEvent(STORE, USER));
        verify(team, never()).acceptByAutomation(any(), any(), anyString());
        assertThat(savedAudit().getStatus()).isEqualTo(RequestDecision.SKIPPED);
    }

    @Test
    void withoutDistanceRuleEveryoneIsAccepted() {
        policy.setDistanceRuleEnabled(false);
        user.setLatitude(null);
        runner.onApplication(new StoreApplicationReceivedEvent(STORE, USER));
        assertThat(savedAudit().getStatus()).isEqualTo(RequestDecision.APPROVED);
    }

    @Test
    void dryRunOnlyAudits() {
        PickupPolicyRunner.RunResult result = runner.runStore(STORE, true);
        assertThat(result.dryRun()).isTrue();
        assertThat(result.confirmed()).isEqualTo(1);
        verify(team, never()).acceptByAutomation(any(), any(), anyString());
        assertThat(savedAudit().getStatus()).isEqualTo(RequestDecision.WOULD_APPROVE);
    }

    @Test
    void approvalsPerRunAreLimited() {
        properties.getAutomation().setMaxRequestApprovalsPerRun(1);
        UUID second = UUID.randomUUID();
        when(team.applicantIds(STORE)).thenReturn(List.of(USER, second));
        PickupPolicyRunner.RunResult result = runner.runAll();
        assertThat(result.evaluated()).isEqualTo(1);
        assertThat(result.messages()).containsExactly("Request automation limit reached (1).");
        verify(team, times(1)).acceptByAutomation(any(), any(), anyString());
    }
}
