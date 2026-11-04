package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.PickupPolicyExcludedDate;
import network.foodsharing.automation.domain.PickupPolicyLinkedStore;
import network.foodsharing.automation.domain.PickupPolicyLinkedStoreMember;
import network.foodsharing.automation.domain.PickupGapGroup;
import network.foodsharing.automation.repository.CleaningRuleExemptionRepository;
import network.foodsharing.automation.repository.PickupGapGroupRepository;
import network.foodsharing.automation.repository.PickupPolicyExcludedDateRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreMemberRepository;
import network.foodsharing.automation.repository.PickupPolicyLinkedStoreRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.LinkRule;
import network.foodsharing.automation.service.PickupPolicyEvaluator.RegionInfo;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Signup;
import network.foodsharing.automation.service.PickupPolicyEvaluator.CleaningSlot;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Spec;
import network.foodsharing.automation.service.PickupPolicyEvaluator.UserInfo;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.fsp.service.FspCleaningQueries;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.service.PickupAutomationQueries;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Database-backed {@link PickupPolicyEvaluator.Data}; also turns policy rows into {@link Spec}s. */
@Component
public class PickupPolicyData implements PickupPolicyEvaluator.Data {

    private final PickupPolicyRepository policies;
    private final PickupPolicyExcludedDateRepository excludedDates;
    private final PickupPolicyLinkedStoreRepository links;
    private final PickupPolicyLinkedStoreMemberRepository linkMembers;
    private final CleaningRuleExemptionRepository exemptions;
    private final StoreService stores;
    private final RegionService regions;
    private final UserService users;
    private final PickupAutomationQueries signups;
    private final FspCleaningQueries cleaning;
    private final HolidayCalendarService holidays;
    private final IntegrationRegistry integrations;
    private final AppProperties properties;
    private final Clock clock;
    private final PickupGapGroupRepository gapGroups;

    public PickupPolicyData(PickupPolicyRepository policies, PickupPolicyExcludedDateRepository excludedDates,
                            PickupPolicyLinkedStoreRepository links, PickupPolicyLinkedStoreMemberRepository linkMembers,
                            CleaningRuleExemptionRepository exemptions, StoreService stores, RegionService regions,
                            UserService users, PickupAutomationQueries signups, FspCleaningQueries cleaning,
                            HolidayCalendarService holidays, IntegrationRegistry integrations,
                            AppProperties properties, Clock clock, PickupGapGroupRepository gapGroups) {
        this.gapGroups = gapGroups;
        this.policies = policies;
        this.excludedDates = excludedDates;
        this.links = links;
        this.linkMembers = linkMembers;
        this.exemptions = exemptions;
        this.stores = stores;
        this.regions = regions;
        this.users = users;
        this.signups = signups;
        this.cleaning = cleaning;
        this.holidays = holidays;
        this.integrations = integrations;
        this.properties = properties;
        this.clock = clock;
    }

    /** The policy with its excluded dates, link rules (own and mirrored) and gap list with the group gap. */
    public Spec spec(PickupPolicy policy) {
        Set<LocalDate> dates = excludedDates.findByPolicyIdOrderByExcludedDate(policy.getId()).stream()
                .map(PickupPolicyExcludedDate::getExcludedDate).collect(Collectors.toCollection(HashSet::new));
        List<PickupPolicyLinkedStore> rules = links.findByPolicyIdOrderByCreatedAtAscIdAsc(policy.getId());
        Map<UUID, List<UUID>> members = linkMembers.findByLinkIdIn(rules.stream().map(PickupPolicyLinkedStore::getId)
                .toList()).stream().collect(Collectors.groupingBy(PickupPolicyLinkedStoreMember::getLinkId,
                Collectors.mapping(PickupPolicyLinkedStoreMember::getStoreId, Collectors.toList())));
        List<LinkRule> linkRules = rules.stream()
                .filter(rule -> !members.getOrDefault(rule.getId(), List.of()).isEmpty())
                .map(rule -> new LinkRule(members.get(rule.getId()), rule.getWindowMode(), rule.getWindowDaysBefore(),
                        rule.getWindowDaysAfter(), rule.getGraceMinutes(), rule.getMessage()))
                .toList();
        List<UUID> gapStoreIds = gapStoreIds(policy);
        return new Spec(policy.getId(), policy.getStoreId(), policy.isEnabled(),
                policy.isGapRuleEnabled(), policy.getMinimumGapDays(), policy.isCleaningRuleEnabled(),
                policy.isExperienceRuleEnabled(), policy.isPublicHolidayRuleEnabled(), Set.copyOf(dates), linkRules,
                gapStoreIds, gapStoreIds.isEmpty() ? 0 : groupMinimumGapDays(policy));
    }

    /** The gap of the policy's gap list (decision 152); 0 without a list. */
    int groupMinimumGapDays(PickupPolicy policy) {
        return policy.getGapGroupId() == null ? 0
                : gapGroups.findById(policy.getGapGroupId()).map(PickupGapGroup::getMinimumGapDays).orElse(0);
    }

    /** The other live stores of the policy's gap list, by name (decision 151); empty without a list. */
    List<UUID> gapStoreIds(PickupPolicy policy) {
        if (policy.getGapGroupId() == null) {
            return List.of();
        }
        List<UUID> others = policies.findByGapGroupId(policy.getGapGroupId()).stream().map(PickupPolicy::getStoreId)
                .filter(id -> !id.equals(policy.getStoreId())).toList();
        return stores.liveStores(others).stream()
                .sorted(java.util.Comparator.comparing(Store::getName).thenComparing(Store::getId))
                .map(Store::getId).toList();
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public ZoneId zone(UUID storeId) {
        return regions.zoneOf(store(storeId).getRegionId());
    }

    @Override
    public String storeName(UUID storeId) {
        return store(storeId).getName();
    }

    @Override
    public UUID regionId(UUID storeId) {
        return store(storeId).getRegionId();
    }

    private Store store(UUID storeId) {
        return stores.require(storeId);
    }

    @Override
    public RegionInfo region(UUID regionId) {
        return regions.find(regionId).map(r -> new RegionInfo(r.getId(), r.getName(), cleaning.cleaningFsps(regionId)
                .stream().map(FspCleaningQueries.CleaningFsp::id).toList()))
                .orElse(new RegionInfo(regionId, "", List.of()));
    }

    @Override
    public UserInfo user(UUID userId) {
        User user = users.require(userId);
        Locale locale = Locale.forLanguageTag(user.getPreferredLanguage() == null ? "de"
                : user.getPreferredLanguage().getValue());
        return new UserInfo(user.getId(), user.getFirstName(), user.getVerifiedAt(), locale);
    }

    @Override
    public List<Signup> signups(UUID storeId, Instant from, Instant to) {
        return signups.signups(List.of(storeId), from, to).stream().map(PickupPolicyData::signup).toList();
    }

    @Override
    public List<Signup> signupsOfUser(UUID userId, Collection<UUID> storeIds, Instant from, Instant to) {
        return signups.signupsOfUser(userId, storeIds, from, to).stream().map(PickupPolicyData::signup).toList();
    }

    static Signup signup(PickupSignup s) {
        return new Signup(s.getStoreId(), s.getUserId(), s.getPickupAt(), s.isConfirmed(), s.getCreatedAt());
    }

    @Override
    public Optional<Instant> lastConfirmedPickup(UUID storeId, UUID userId, Instant before) {
        return signups.lastConfirmedPickup(storeId, userId, before);
    }

    @Override
    public Optional<Instant> lastConfirmedCleaning(Collection<UUID> fspIds, UUID userId, Instant before) {
        return cleaning.lastConfirmedCleaning(fspIds, userId, before);
    }

    @Override
    public List<Instant> cleaningSignupsOfUser(UUID userId, Collection<UUID> fspIds, Instant from, Instant to) {
        return cleaning.cleaningSignupsOfUser(userId, fspIds, from, to).stream()
                .map(FspCleaningQueries.CleaningSignup::shiftAt).toList();
    }

    @Override
    public List<CleaningSlot> freeCleaningSlots(Collection<UUID> fspIds, Instant from, Instant to) {
        return cleaning.freeCleaningSlots(fspIds, from, to).stream()
                .map(slot -> new CleaningSlot(slot.at(), slot.fspId(), slot.fspName())).toList();
    }

    @Override
    public Optional<String> holidayName(UUID storeId, LocalDate date) {
        return holidays.holidayName(date, holidays.placeOf(regionId(storeId)));
    }

    /** The holiday place of the store's region (cached per request by {@link CachingPolicyData}). */
    HolidayCalendarService.Place holidayPlace(UUID storeId) {
        return holidays.placeOf(regionId(storeId));
    }

    /** All holidays of a year at the place. */
    List<HolidayCalendarService.Holiday> holidays(int year, HolidayCalendarService.Place place) {
        return holidays.holidays(year, place);
    }

    /** Those of the users with a confirmed pickup in the store before {@code before}. */
    Set<UUID> usersWithConfirmedPickup(UUID storeId, Collection<UUID> userIds, Instant before) {
        return signups.usersWithConfirmedPickup(storeId, userIds, before);
    }

    @Override
    public boolean cleaningExempt(UUID regionId, UUID userId) {
        return exemptions.existsByRegionIdAndUserId(regionId, userId);
    }

    @Override
    public Optional<Spec> enabledPolicy(UUID storeId) {
        return policies.findByStoreId(storeId).filter(PickupPolicy::isEnabled).map(this::spec);
    }

    @Override
    public Instant extendDeadline(Instant start, Instant deadline) {
        return integrations.breaker(Integration.SMTP).extendDeadline(start, deadline);
    }

    @Override
    public String cleaningLink(UUID fspId) {
        return properties.getBaseUrl().replaceFirst("/+$", "") + cleaning.cleaningPath(fspId);
    }

    @Override
    public int cleaningBackCheckMonths() {
        return properties.getAutomation().getCleaningBackCheckMonths();
    }

    @Override
    public int minimumFreeCleaningSlots() {
        return properties.getAutomation().getMinimumFreeCleaningSlots();
    }
}
