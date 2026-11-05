package network.foodsharing.automation.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.LinkMatch;
import network.foodsharing.automation.service.PickupPolicyEvaluator.LinkRule;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.config.AppProperties;
import network.foodsharing.pickup.domain.PickupEligibilityReason;
import network.foodsharing.pickup.service.PickupRuleGate;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The linked-store step of the sign-up dialog (decision 167 S3, proposal §3.4): per link rule of the store's enabled
 * policy either the caller's linked pickup that already satisfies it ({@code satisfiedBy}), or the future pickups with
 * a free slot of the linked stores in the rule's window, each with the eligibility reasons and the rules of its own
 * store (counting the pickup here as booked), plus the linked stores where the caller is not in the team. Empty for
 * store managers (never blocked), while the rules are off and without link rules.
 */
@Service
public class LinkedOptionsService {

    /** One bookable pickup of a linked store. */
    public record Option(Store store, Instant pickupAt, int freeCount, List<PickupEligibilityReason> reasons,
                         List<PickupRuleGate.RuleBlock> blocks) {
    }

    /** The rules and the stores named in the options' blocks. */
    public record View(List<RuleOptions> rules, Map<UUID, Store> blockStores) {

        static final View NONE = new View(List.of(), Map.of());
    }

    /** One link rule: {@code from}/{@code to} start of its first and last day; {@code satisfiedBy} or options. */
    public record RuleOptions(List<Store> stores, LinkRule rule, Instant from, Instant to, Store satisfiedStore,
                              Instant satisfiedAt, List<Option> options, List<Store> notMember) {
    }

    private final PickupPolicyRepository policies;
    private final PickupPolicyEvaluator evaluator;
    private final PickupPolicyData data;
    private final PickupRuleGateImpl gate;
    private final PickupScheduleService schedule;
    private final StoreService stores;
    private final StorePermissionService rights;
    private final CurrentActorService currentActor;
    private final AppProperties properties;

    public LinkedOptionsService(PickupPolicyRepository policies, PickupPolicyEvaluator evaluator,
                                PickupPolicyData data, PickupRuleGateImpl gate, PickupScheduleService schedule,
                                StoreService stores, StorePermissionService rights, CurrentActorService currentActor,
                                AppProperties properties) {
        this.policies = policies;
        this.evaluator = evaluator;
        this.data = data;
        this.gate = gate;
        this.schedule = schedule;
        this.stores = stores;
        this.rights = rights;
        this.currentActor = currentActor;
        this.properties = properties;
    }

    /** @throws network.foodsharing.common.error.ApiException 404, 403 unless maySeePickups */
    @Transactional(readOnly = true)
    public View linkedOptions(UUID storeId, Instant pickupAt) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.Rights actorRights = rights.rights(actor, store);
        StorePermissionService.require(actorRights.maySeePickups());
        if (!properties.getAutomation().isEnabled() || PickupScheduleService.isRuleExempt(actorRights)) {
            return View.NONE;
        }
        Optional<PickupPolicy> policy = policies.findByStoreId(storeId).filter(PickupPolicy::isEnabled);
        if (policy.isEmpty()) {
            return View.NONE;
        }
        Spec spec = data.spec(policy.get());
        if (spec.links().isEmpty()) {
            return View.NONE;
        }
        UUID userId = actor.userId();
        CachingPolicyData cached = new CachingPolicyData(data);
        Instant now = cached.now();
        ZoneId zone = cached.zone(storeId);
        List<Optional<LinkMatch>> matches = evaluator.linkMatches(spec, pickupAt, userId, cached);
        Map<UUID, Store> byId = stores.liveStores(spec.links().stream().flatMap(l -> l.storeIds().stream())
                .distinct().toList()).stream().collect(Collectors.toMap(Store::getId, s -> s));
        List<PickupRuleGate.Planned> planned = List.of(new PickupRuleGate.Planned(storeId, pickupAt));
        List<RuleOptions> result = new ArrayList<>();
        for (int i = 0; i < spec.links().size(); i++) {
            LinkRule rule = spec.links().get(i);
            PickupPolicyEvaluator.Params window = PickupPolicyEvaluator.linkParams(rule, pickupAt, zone);
            Instant end = window.to().atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
            List<Store> ruleStores = rule.storeIds().stream().map(byId::get).filter(java.util.Objects::nonNull)
                    .toList();
            if (ruleStores.isEmpty()) {
                continue;
            }
            Optional<LinkMatch> match = matches.get(i);
            if (match.isPresent()) {
                result.add(new RuleOptions(ruleStores, rule, window.from(), window.to(),
                        byId.get(match.get().storeId()), match.get().pickupAt(), List.of(), List.of()));
                continue;
            }
            List<Option> options = new ArrayList<>();
            List<Store> notMember = new ArrayList<>();
            Instant from = window.from().isAfter(now) ? window.from() : now;
            for (Store linked : ruleStores) {
                StorePermissionService.Rights linkedRights = rights.rights(actor, linked);
                UserTeamStatus status = rights.teamStatus(userId, linked.getId());
                if (!linkedRights.maySeePickups()
                        || status != UserTeamStatus.MEMBER && status != UserTeamStatus.COORDINATOR) {
                    notMember.add(linked);
                    continue;
                }
                options.addAll(options(linked, linkedRights, userId, from, end, planned));
            }
            options.sort(Comparator.comparing(Option::pickupAt).thenComparing(o -> o.store().getName()));
            result.add(new RuleOptions(ruleStores, rule, window.from(), window.to(), null, null, options,
                    notMember));
        }
        List<UUID> named = result.stream().flatMap(r -> r.options().stream()).flatMap(o -> o.blocks().stream())
                .flatMap(b -> b.params().storeIds().stream()).distinct().toList();
        return new View(result, named.isEmpty() ? Map.of() : stores.storesById(named).stream()
                .collect(Collectors.toMap(Store::getId, s -> s)));
    }

    private List<Option> options(Store linked, StorePermissionService.Rights linkedRights, UUID userId, Instant from,
                                 Instant to, List<PickupRuleGate.Planned> planned) {
        if (!from.isBefore(to)) {
            return List.of();
        }
        List<PickupSchedule.Slot> free = schedule.schedule(linked, from, to).stream()
                .filter(slot -> slot.pickupAt().isAfter(from.minusMillis(1)) && slot.slotCount() > slot.occupied()
                        && !slot.hasSignup(userId))
                .toList();
        if (free.isEmpty()) {
            return List.of();
        }
        PickupScheduleService.EligibilityContext context = schedule.context(linked, userId);
        Map<Instant, List<PickupRuleGate.RuleBlock>> blocks = PickupScheduleService.isRuleExempt(linkedRights)
                ? Map.of() : gate.blocks(linked.getId(), userId, free.stream().map(PickupSchedule.Slot::pickupAt)
                        .toList(), planned);
        return free.stream().map(slot -> new Option(linked, slot.pickupAt(), slot.slotCount() - slot.occupied(),
                context.evaluate(slot.pickupAt(), slot).reasons(), blocks.getOrDefault(slot.pickupAt(), List.of())))
                .toList();
    }
}
