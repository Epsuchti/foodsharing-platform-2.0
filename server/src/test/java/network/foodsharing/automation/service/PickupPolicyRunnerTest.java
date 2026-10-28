package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.domain.PolicyRule;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Decision;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Reason;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.pickup.service.PickupSignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Runner paths: event vs job, dry run, deferred (no audit), repeated decisions, failures (M4 B4). */
class PickupPolicyRunnerTest {

    static final UUID STORE = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final Instant PICKUP = Instant.parse("2026-11-12T18:00:00Z");
    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    PickupPolicyRepository policies = mock(PickupPolicyRepository.class);
    PickupPolicyAuditRepository audits = mock(PickupPolicyAuditRepository.class);
    PickupPolicyEvaluator evaluator = mock(PickupPolicyEvaluator.class);
    PickupPolicyData data = mock(PickupPolicyData.class);
    PickupSignupService pickups = mock(PickupSignupService.class);
    PickupAutomationQueries queries = mock(PickupAutomationQueries.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    AppProperties properties = new AppProperties();
    PickupPolicy policy = new PickupPolicy();
    PickupPolicyRunner runner;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/automation/messages");
        messages.setDefaultEncoding("UTF-8");
        runner = new PickupPolicyRunner(policies, audits, evaluator, data, pickups, queries, history, properties,
                JsonMapper.builder().build(), mock(PlatformTransactionManager.class));
        policy.setStoreId(STORE);
        policy.setEnabled(true);
        policy.setDryRun(false);
        when(policies.findByStoreId(STORE)).thenReturn(Optional.of(policy));
        when(policies.findByEnabledTrue()).thenReturn(List.of(policy));
        when(data.spec(policy)).thenReturn(new PickupPolicyEvaluator.Spec(null, STORE, true, false, false, 0, false,
                false, false, Set.of(), List.of()));
        when(data.user(USER)).thenReturn(new PickupPolicyEvaluator.UserInfo(USER, "Sarah", null, Locale.GERMAN));
        when(data.regionId(STORE)).thenReturn(UUID.randomUUID());
        when(data.now()).thenReturn(NOW);
        when(evaluator.texts(any())).thenAnswer(inv -> new PolicyTexts(messages, inv.getArgument(0)));
        PickupSignup signup = new PickupSignup();
        signup.setStoreId(STORE);
        signup.setUserId(USER);
        signup.setPickupAt(PICKUP);
        when(queries.unconfirmedFuture(any(), eq(NOW))).thenReturn(List.of(signup));
    }

    void decide(Decision decision) {
        when(evaluator.evaluate(any(), eq(PICKUP), eq(USER), any())).thenReturn(decision);
    }

    static Decision allowed(PolicyRule rule, String userMessage) {
        return new Decision(true, false, false, List.of(new Reason(rule, "ok")), userMessage);
    }

    static Decision declined() {
        return new Decision(false, false, false, List.of(new Reason(PolicyRule.GAP, "Zu wenig Abstand.")), null);
    }

    PickupPolicyAudit savedAudit() {
        ArgumentCaptor<PickupPolicyAudit> captor = ArgumentCaptor.forClass(PickupPolicyAudit.class);
        verify(audits).save(captor.capture());
        return captor.getValue();
    }

    void signUp() {
        runner.onSignedUp(new PickupEvents.PickupSignedUpEvent(STORE, PICKUP, USER, false, NOW));
    }

    @Test
    void eventPathConfirmsWithTheCleaningMessage() {
        decide(allowed(PolicyRule.CLEANING_REMINDER, "Bitte putzen"));
        signUp();
        verify(pickups).confirmByAutomation(STORE, PICKUP, USER, PickupPolicyRunner.ACTOR, "Bitte putzen");
        PickupPolicyAudit audit = savedAudit();
        assertThat(audit.getDecision()).isEqualTo(PickupPolicyDecision.CONFIRMED);
        assertThat(audit.getReasons()).contains("CLEANING_REMINDER");
        assertThat(audit.getUserMessage()).isEqualTo("Bitte putzen");
    }

    @Test
    void confirmedSignupsAndDisabledAutomationAreIgnored() {
        decide(declined());
        runner.onSignedUp(new PickupEvents.PickupSignedUpEvent(STORE, PICKUP, USER, true, NOW));
        properties.getAutomation().setEnabled(false);
        signUp();
        verify(evaluator, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void declineRemovesWithReasonsAndFooter() {
        decide(declined());
        signUp();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(pickups).removeByAutomation(eq(STORE), eq(PICKUP), eq(USER), eq(PickupPolicyRunner.ACTOR),
                message.capture());
        assertThat(message.getValue()).startsWith("Zu wenig Abstand.\n\nDiese Aktion wurde automatisch ausgeführt.");
        assertThat(savedAudit().getDecision()).isEqualTo(PickupPolicyDecision.DECLINED);
    }

    @Test
    void dryRunOnlyAudits() {
        policy.setDryRun(true);
        decide(declined());
        signUp();
        verify(pickups, never()).removeByAutomation(any(), any(), any(), anyString(), any());
        PickupPolicyAudit audit = savedAudit();
        assertThat(audit.getDecision()).isEqualTo(PickupPolicyDecision.WOULD_DECLINE);
        assertThat(audit.isDryRun()).isTrue();
    }

    @Test
    void globalDryRunAndForcedDryRun() {
        properties.getAutomation().setDryRun(true);
        decide(allowed(PolicyRule.ALL_RULES_PASSED, null));
        PickupPolicyRunner.RunResult result = runner.runAll();
        assertThat(result.dryRun()).isTrue();
        assertThat(result.confirmed()).isEqualTo(1);
        verify(pickups, never()).confirmByAutomation(any(), any(), any(), anyString(), any());
        assertThat(savedAudit().getDecision()).isEqualTo(PickupPolicyDecision.WOULD_CONFIRM);
    }

    @Test
    void deferredWritesNoAudit() {
        decide(new Decision(false, true, true, List.of(new Reason(PolicyRule.LINKED_STORE, "wartet")), null));
        PickupPolicyRunner.RunResult result = runner.runStore(STORE, false);
        assertThat(result.deferred()).isEqualTo(1);
        verify(audits, never()).save(any());
    }

    @Test
    void jobPathDoesNotRepeatTheSameSkippedDecision() {
        decide(new Decision(false, true, false, List.of(new Reason(PolicyRule.PUBLIC_HOLIDAY, "Feiertag")), null));
        PickupPolicyAudit previous = new PickupPolicyAudit();
        previous.setDecision(PickupPolicyDecision.SKIPPED);
        when(audits.findFirstByStoreIdAndUserIdAndPickupAtOrderByCreatedAtDesc(STORE, USER, PICKUP))
                .thenReturn(Optional.of(previous));
        assertThat(runner.runAll().skipped()).isEqualTo(1);
        verify(audits, never()).save(any());
        // the event path always audits
        signUp();
        assertThat(savedAudit().getDecision()).isEqualTo(PickupPolicyDecision.SKIPPED);
    }

    @Test
    void failureWritesAFailedAudit() {
        when(evaluator.evaluate(any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        PickupPolicyRunner.RunResult result = runner.runAll();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.messages()).containsExactly("boom");
        PickupPolicyAudit audit = savedAudit();
        assertThat(audit.getDecision()).isEqualTo(PickupPolicyDecision.FAILED);
        assertThat(audit.getError()).isEqualTo("boom");
    }
}
