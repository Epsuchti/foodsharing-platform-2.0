package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Signup;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.config.AppProperties;
import network.foodsharing.pickup.service.PickupRuleGate.RuleBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * The rule gate (decision 167 S2): which evaluations block, batching over a pickup list, managers' exemption lives in
 * the pickup module, the refusal audit. Now = Monday 2026-11-02 10:00 Zurich.
 */
class PickupRuleGateImplTest {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID STORE = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();

    PickupPolicyRepository policies = mock(PickupPolicyRepository.class);
    PickupPolicyAuditRepository audits = mock(PickupPolicyAuditRepository.class);
    PickupPolicyData data = mock(PickupPolicyData.class);
    AppProperties properties = new AppProperties();
    PickupPolicy policy = new PickupPolicy();
    PickupRuleGateImpl gate;

    static Instant at(String local) {
        return java.time.LocalDateTime.parse(local).atZone(ZURICH).toInstant();
    }

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/automation/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        gate = new PickupRuleGateImpl(policies, audits, new PickupPolicyEvaluator(messages), data, properties,
                JsonMapper.builder().build(), mock(PlatformTransactionManager.class),
                mock(network.foodsharing.automation.repository.PickupLinkPendingRepository.class));
        policy.setId(UUID.randomUUID());
        policy.setStoreId(STORE);
        policy.setEnabled(true);
        when(policies.findByStoreId(STORE)).thenReturn(Optional.of(policy));
        when(data.now()).thenReturn(NOW);
        when(data.zone(STORE)).thenReturn(ZURICH);
        when(data.storeName(STORE)).thenReturn("Store GmbH");
        when(data.regionId(STORE)).thenReturn(UUID.randomUUID());
        when(data.user(any())).thenAnswer(inv -> new PickupPolicyEvaluator.UserInfo(inv.getArgument(0), "Sarah",
                null, Locale.GERMAN));
        // the person's confirmed pickup on Tuesday 10.11.
        when(data.signupsOfUser(eq(USER), anyCollection(), any(), any())).thenReturn(List.of(
                new Signup(STORE, USER, at("2026-11-10T19:00"), true, NOW.minusSeconds(3600))));
        when(data.signups(eq(STORE), any(), any())).thenReturn(List.of());
    }

    static final UUID LINKED = UUID.randomUUID();

    /** Only a link rule: a pickup in LINKED on the same day up to 2 days after. */
    void linkRule() {
        when(data.spec(policy)).thenReturn(new Spec(policy.getId(), STORE, true, false, 0, false, false, false,
                Set.of(), List.of(new PickupPolicyEvaluator.LinkRule(List.of(LINKED),
                        network.foodsharing.automation.domain.LinkedStoreWindowMode.LAST_DAYS, 0, 2, 120, null))));
        when(data.zone(LINKED)).thenReturn(ZURICH);
        when(data.storeName(LINKED)).thenReturn("Linked AG");
        when(data.signupsOfUser(eq(USER), anyCollection(), any(), any())).thenReturn(List.of());
    }

    @Test
    void aMissingLinkedPickupBlocksUpFrontWithTheWindow() {
        linkRule();
        Map<Instant, List<RuleBlock>> blocks = gate.blocks(STORE, USER, List.of(at("2026-11-11T19:00")));
        RuleBlock block = blocks.get(at("2026-11-11T19:00")).getFirst();
        assertThat(block.rule()).isEqualTo("LINKED_STORE");
        assertThat(block.params().storeIds()).containsExactly(LINKED);
        assertThat(block.params().from()).isEqualTo(at("2026-11-11T00:00"));
        assertThat(block.params().to()).isEqualTo(at("2026-11-13T00:00"));
    }

    @Test
    void aLinkedPickupBookedInTheSameRequestSatisfiesTheRule() {
        linkRule();
        Instant pickup = at("2026-11-11T19:00");
        assertThat(gate.check(STORE, USER, pickup, List.of(new network.foodsharing.pickup.service.PickupRuleGate
                .Planned(LINKED, at("2026-11-12T18:30"))))).isEmpty();
        // outside the window it does not count
        assertThat(gate.check(STORE, USER, pickup, List.of(new network.foodsharing.pickup.service.PickupRuleGate
                .Planned(LINKED, at("2026-11-14T18:30"))))).extracting(RuleBlock::rule).containsExactly("LINKED_STORE");
    }

    @Test
    void aSignedOutLinkWithinItsDeadlineDoesNotBlockAfterwardsItDoes() {
        linkRule();
        Instant pickup = at("2026-11-11T19:00");
        when(data.linkPendingDeadlines(STORE, USER)).thenReturn(Map.of(pickup, NOW.plusSeconds(600)));
        assertThat(gate.blocks(STORE, USER, List.of(pickup))).isEmpty();
        when(data.linkPendingDeadlines(STORE, USER)).thenReturn(Map.of(pickup, NOW.minusSeconds(1)));
        assertThat(gate.blocks(STORE, USER, List.of(pickup))).containsOnlyKeys(pickup);
    }

    void rules(boolean gap, boolean experience, Set<LocalDate> excluded) {
        when(data.spec(policy)).thenReturn(new Spec(policy.getId(), STORE, true, gap, 2, false, experience, false,
                excluded, List.of()));
    }

    @Test
    void blocksOnlyThePickupsThatBreakARuleWithTheirParams() {
        rules(true, false, Set.of());
        Map<Instant, List<RuleBlock>> blocks = gate.blocks(STORE, USER, List.of(at("2026-11-11T19:00"),
                at("2026-11-12T19:00"), at("2026-11-13T19:00")));
        assertThat(blocks).containsOnlyKeys(at("2026-11-11T19:00"), at("2026-11-12T19:00"));
        RuleBlock block = blocks.get(at("2026-11-12T19:00")).getFirst();
        assertThat(block.rule()).isEqualTo("GAP");
        assertThat(block.params().lastPickupAt()).isEqualTo(at("2026-11-10T19:00"));
        assertThat(block.params().nextPossibleAt()).isEqualTo(at("2026-11-13T00:00"));
        assertThat(block.params().gapDays()).isEqualTo(2);
        assertThat(block.text()).contains("erforderlich sind mindestens 2 Tage");
    }

    @Test
    void aWholeListCostsOneSignupQueryPerStoreSet() {
        rules(true, false, Set.of());
        gate.blocks(STORE, USER, List.of(at("2026-11-11T19:00"), at("2026-11-12T19:00"), at("2026-11-13T19:00"),
                at("2026-11-20T19:00")));
        verify(data, times(1)).signupsOfUser(eq(USER), anyCollection(), any(), any());
        verify(data, times(1)).user(USER);
    }

    @Test
    void experienceOfCoSignedPeopleIsResolvedInOneQuery() {
        rules(false, true, Set.of());
        when(data.signups(eq(STORE), any(), any())).thenReturn(List.of(
                new Signup(STORE, OTHER, at("2026-11-12T19:00"), true, NOW)));
        when(data.usersWithConfirmedPickup(eq(STORE), anyCollection(), eq(NOW))).thenReturn(Set.of(OTHER));
        Map<Instant, List<RuleBlock>> blocks = gate.blocks(STORE, USER, List.of(at("2026-11-12T19:00"),
                at("2026-11-13T19:00")));
        // 12.11.: an experienced person is in the pickup; 13.11.: nobody, so the newcomer is blocked
        assertThat(blocks).containsOnlyKeys(at("2026-11-13T19:00"));
        assertThat(blocks.get(at("2026-11-13T19:00")).getFirst().rule()).isEqualTo("EXPERIENCE");
        verify(data, times(1)).usersWithConfirmedPickup(eq(STORE), anyCollection(), eq(NOW));
        verify(data, never()).lastConfirmedPickup(any(), any(), any());
    }

    @Test
    void skippedDaysAndTheLastTwoDaysBeforeAPickupAreNotBlocked() {
        rules(true, false, Set.of(LocalDate.parse("2026-11-11")));
        // 03.11. is within the near-pickup window, 11.11. is an excluded date: the managers decide
        assertThat(gate.blocks(STORE, USER, List.of(at("2026-11-03T19:00"), at("2026-11-11T19:00")))).isEmpty();
    }

    @Test
    void rulesOffOrAutomationOffBlockNothing() {
        rules(true, false, Set.of());
        policy.setEnabled(false);
        assertThat(gate.blocks(STORE, USER, List.of(at("2026-11-11T19:00")))).isEmpty();
        policy.setEnabled(true);
        properties.getAutomation().setEnabled(false);
        assertThat(gate.blocks(STORE, USER, List.of(at("2026-11-11T19:00")))).isEmpty();
        verify(data, never()).spec(any());
    }

    @Test
    void batchVariantSkipsStoresWithoutActiveRules() {
        rules(true, false, Set.of());
        UUID quiet = UUID.randomUUID();
        PickupPolicy off = new PickupPolicy();
        off.setStoreId(quiet);
        off.setEnabled(false);
        when(policies.findByStoreIdIn(any())).thenReturn(List.of(policy, off));
        Map<UUID, Map<Instant, List<RuleBlock>>> blocks = gate.blocks(USER, Map.of(STORE,
                List.of(at("2026-11-11T19:00")), quiet, List.of(at("2026-11-11T19:00"))));
        assertThat(blocks).containsOnlyKeys(STORE);
    }

    @Test
    void aRefusalIsAuditedAsDeclinedWithRuleAndText() {
        gate.refused(STORE, USER, at("2026-11-11T19:00"), List.of(new RuleBlock("GAP",
                new network.foodsharing.pickup.service.PickupRuleGate.BlockParams(null, null, 2, List.of(), null,
                        null, null, null, null, null, null, null), "Zu wenig Abstand.")));
        ArgumentCaptor<PickupPolicyAudit> captor = ArgumentCaptor.forClass(PickupPolicyAudit.class);
        verify(audits).save(captor.capture());
        PickupPolicyAudit audit = captor.getValue();
        assertThat(audit.getDecision()).isEqualTo(PickupPolicyDecision.DECLINED);
        assertThat(audit.getPolicyId()).isEqualTo(policy.getId());
        assertThat(audit.getReasons()).isEqualTo("[{\"rule\":\"GAP\",\"text\":\"Zu wenig Abstand.\"}]");
    }
}
