package network.foodsharing.automation.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.PickupLinkPending;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.domain.PickupPolicyLinkedStore;
import network.foodsharing.automation.domain.PickupPolicyLinkedStoreMember;
import network.foodsharing.automation.domain.PolicyRule;
import network.foodsharing.automation.repository.PickupLinkPendingRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreMemberRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Decision;
import network.foodsharing.automation.service.PickupPolicyEvaluator.LinkMatch;
import network.foodsharing.automation.service.PickupPolicyEvaluator.LinkRule;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Signup;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.pickup.service.PickupFormat;
import network.foodsharing.store.service.StoreNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-ups whose linked-store pickup went away (decision 165 S3, Q4). When the person's pickup in a linked store Y
 * ends, every future sign-up of theirs in a store X whose enabled policy links Y is checked: one that now misses its
 * linked pickup gets a {@code pickup_link_pending} row (deadline = now + the rule's {@code graceMinutes}, at least
 * {@link PickupPolicyEvaluator#MINIMUM_GRACE_MINUTES}), history {@link AutomationAction#LINK_MISSING} and a
 * PICKUP_LINK_MISSING bell + push. A new sign-up that satisfies the link again removes the row; the sign-up ending
 * removes it, too. After the deadline the {@code automation-evaluate} job frees the slot
 * ({@link PickupPolicyRunner#freeLinkMissing}). Both listeners run before the commit of the sign-up/sign-out, so the
 * pickup list shows the state right away. While X's rules are off nothing is freed; rows of past pickups go.
 */
@Service
public class LinkPendingService {

    private static final Logger log = LoggerFactory.getLogger(LinkPendingService.class);

    private final PickupLinkPendingRepository rows;
    private final PickupPolicyRepository policies;
    private final PickupPolicyLinkedStoreRepository links;
    private final PickupPolicyLinkedStoreMemberRepository linkMembers;
    private final PickupPolicyEvaluator evaluator;
    private final PickupPolicyData data;
    private final PickupAutomationQueries queries;
    private final PickupPolicyRunner runner;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final TransactionTemplate tx;
    private final TransactionTemplate ownTx;

    public LinkPendingService(PickupLinkPendingRepository rows, PickupPolicyRepository policies,
                              PickupPolicyLinkedStoreRepository links,
                              PickupPolicyLinkedStoreMemberRepository linkMembers, PickupPolicyEvaluator evaluator,
                              PickupPolicyData data, PickupAutomationQueries queries, PickupPolicyRunner runner,
                              HistoryService history, ApplicationEventPublisher events, AppProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.rows = rows;
        this.policies = policies;
        this.links = links;
        this.linkMembers = linkMembers;
        this.evaluator = evaluator;
        this.data = data;
        this.queries = queries;
        this.runner = runner;
        this.history = history;
        this.events = events;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.ownTx = new TransactionTemplate(transactionManager);
        this.ownTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** A sign-up ended: its own pending row goes; sign-ups elsewhere that linked it may now miss their link. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onSignedOut(PickupEvents.PickupSignedOutEvent event) {
        tx.executeWithoutResult(status -> {
            rows.findByStoreIdAndPickupAtAndUserId(event.storeId(), event.pickupAt(), event.userId())
                    .ifPresent(rows::delete);
            if (properties.getAutomation().isEnabled() && event.pickupAt().isAfter(event.at())) {
                recordMissing(event.storeId(), event.userId());
            }
        });
    }

    /** A new sign-up: pending rows of the person whose links are satisfied again go. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onSignedUp(PickupEvents.PickupSignedUpEvent event) {
        tx.executeWithoutResult(status -> {
            for (PickupLinkPending row : rows.findByUserId(event.userId())) {
                Optional<Spec> spec = policies.findByStoreId(row.getStoreId()).filter(PickupPolicy::isEnabled)
                        .map(data::spec);
                if (spec.isPresent() && satisfied(spec.get(), row.getPickupAt(), row.getUserId())) {
                    rows.delete(row);
                }
            }
        });
    }

    /** Every link rule of the policy has the person's linked pickup for this sign-up. */
    boolean satisfied(Spec spec, Instant pickupAt, UUID userId) {
        return evaluator.linkMatches(spec, pickupAt, userId, data).stream().allMatch(Optional::isPresent);
    }

    /**
     * The person's future sign-ups in stores whose enabled policy links {@code linkedStoreId}: those that now miss
     * their linked pickup (the rules would decline them for it) get a pending row, a history entry and a bell.
     *
     * @return the rows written
     */
    List<PickupLinkPending> recordMissing(UUID linkedStoreId, UUID userId) {
        List<PickupLinkPending> written = new ArrayList<>();
        Instant now = data.now();
        for (PickupPolicy policy : policiesLinking(linkedStoreId)) {
            Spec spec = data.spec(policy);
            for (Signup signup : data.signupsOfUser(userId, List.of(policy.getStoreId()), now,
                    now.plus(PickupPolicyEvaluator.HORIZON))) {
                if (rows.findByStoreIdAndPickupAtAndUserId(signup.storeId(), signup.pickupAt(), userId).isPresent()) {
                    continue;
                }
                Decision decision = evaluator.evaluate(spec, signup.pickupAt(), userId, data);
                boolean missing = !decision.allowed() && !decision.skipped() && !decision.deferred()
                        && decision.reasons().stream().anyMatch(r -> r.rule() == PolicyRule.LINKED_STORE);
                if (!missing) {
                    continue;
                }
                List<Optional<LinkMatch>> matches = evaluator.linkMatches(spec, signup.pickupAt(), userId, data);
                LinkRule rule = spec.links().get(Math.max(0, matches.indexOf(Optional.<LinkMatch>empty())));
                written.add(record(policy, signup, linkedStoreId, rule, now));
            }
        }
        return written;
    }

    private PickupLinkPending record(PickupPolicy policy, Signup signup, UUID linkedStoreId, LinkRule rule,
                                     Instant now) {
        Instant deadline = now.plus(Duration.ofMinutes(Math.max(PickupPolicyEvaluator.MINIMUM_GRACE_MINUTES,
                rule.graceMinutes())));
        PickupLinkPending row = new PickupLinkPending();
        row.setStoreId(signup.storeId());
        row.setPickupAt(signup.pickupAt());
        row.setUserId(signup.userId());
        row.setLinkedStoreId(linkedStoreId);
        row.setDeadlineAt(deadline);
        rows.save(row);
        UUID regionId = data.regionId(signup.storeId());
        history.record(history.event(AutomationAction.LINK_MISSING, policy.getId())
                .actorAutomation(PickupPolicyRunner.ACTOR).secondarySubject(signup.userId()).region(regionId)
                .metadata("storeId", signup.storeId().toString()).metadata("pickupAt", signup.pickupAt().toString())
                .metadata("linkedStoreId", linkedStoreId.toString()).metadata("deadlineAt", deadline.toString())
                .build());
        ZoneId zone = data.zone(signup.storeId());
        StoreNotifications.send(events, NotificationRequest.to(List.of(signup.userId()),
                        NotificationType.PICKUP_LINK_MISSING)
                .param("storeId", signup.storeId().toString()).param("storeName", data.storeName(signup.storeId()))
                .param("pickupAt", signup.pickupAt().toString()).param("when", PickupFormat.when(signup.pickupAt(), zone))
                .param("linkedStoreName", data.storeName(linkedStoreId)).param("deadline", deadline.toString())
                .param("deadlineWhen", PickupFormat.when(deadline, zone))
                .link("/stores/" + signup.storeId() + "/pickups").icon("unlink").senderRegion(regionId).build());
        log.debug("Link missing store={} pickupAt={} user={} until {}", signup.storeId(), signup.pickupAt(),
                signup.userId(), deadline);
        return row;
    }

    /** Enabled policies (other stores) with a link rule, own or mirrored, that names the store. */
    private List<PickupPolicy> policiesLinking(UUID storeId) {
        Set<UUID> linkIds = new HashSet<>();
        for (PickupPolicyLinkedStoreMember member : linkMembers.findByStoreId(storeId)) {
            linkIds.add(member.getLinkId());
        }
        if (linkIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> policyIds = new HashSet<>();
        for (PickupPolicyLinkedStore link : links.findAllById(linkIds)) {
            policyIds.add(link.getPolicyId());
        }
        return policies.findAllById(policyIds).stream()
                .filter(p -> p.isEnabled() && !p.getStoreId().equals(storeId)).toList();
    }

    /**
     * Job step ({@code automation-evaluate}): rows of past pickups go; every row whose deadline has passed is decided
     * — freed when the link is still missing, removed when it is satisfied again. Rows of disabled policies wait.
     *
     * @return the failures (messages)
     */
    public List<String> freeExpired() {
        List<String> messages = new ArrayList<>();
        if (!properties.getAutomation().isEnabled()) {
            return messages;
        }
        Instant now = data.now();
        ownTx.executeWithoutResult(status -> rows.deleteAll(rows.findByPickupAtLessThan(now)));
        List<PickupLinkPending> due = ownTx.execute(status -> rows.findByDeadlineAtLessThanEqualOrderByDeadlineAtAscIdAsc(now));
        for (PickupLinkPending row : due == null ? List.<PickupLinkPending>of() : due) {
            Optional<PickupPolicy> policy = ownTx.execute(status -> policies.findByStoreId(row.getStoreId())
                    .filter(PickupPolicy::isEnabled));
            if (policy == null || policy.isEmpty()) {
                continue;
            }
            Optional<Signup> signup = ownTx.execute(status -> queries.signup(row.getStoreId(), row.getUserId(),
                    row.getPickupAt()).map(PickupPolicyData::signup));
            if (signup == null || signup.isEmpty()) {
                ownTx.executeWithoutResult(status -> rows.findById(row.getId()).ifPresent(rows::delete));
                continue;
            }
            PickupPolicyDecision decision = runner.freeLinkMissing(policy.get(), signup.get(), messages);
            if (decision == PickupPolicyDecision.CONFIRMED) {
                // the link is there again, or the rules no longer apply (last two days): nothing left to wait for
                ownTx.executeWithoutResult(status -> rows.findById(row.getId()).ifPresent(rows::delete));
            }
        }
        return messages;
    }
}
