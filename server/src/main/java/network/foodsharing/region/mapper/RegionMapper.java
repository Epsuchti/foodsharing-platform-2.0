package network.foodsharing.region.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import network.foodsharing.api.model.AgeBand;
import network.foodsharing.api.model.GroupRef;
import network.foodsharing.api.model.IntroPickupCategory;
import network.foodsharing.api.model.IntroPickupRegionSettings;
import network.foodsharing.api.model.OnboardingRegionSettings;
import network.foodsharing.api.model.Permission;
import network.foodsharing.api.model.PickupRule;
import network.foodsharing.api.model.PublicIntroPickup;
import network.foodsharing.api.model.PublicRegion;
import network.foodsharing.api.model.PublicRegionNode;
import network.foodsharing.api.model.RegionAdminItem;
import network.foodsharing.api.model.RegionDetail;
import network.foodsharing.api.model.RegionFunctionAssignment;
import network.foodsharing.api.model.RegionImportRow;
import network.foodsharing.api.model.RegionImportStatus;
import network.foodsharing.api.model.RegionJoinMode;
import network.foodsharing.api.model.RegionMatch;
import network.foodsharing.api.model.RegionMember;
import network.foodsharing.api.model.RegionMembershipState;
import network.foodsharing.api.model.RegionOnboardingStep;
import network.foodsharing.api.model.RegionOptionPermissions;
import network.foodsharing.api.model.RegionOptions;
import network.foodsharing.api.model.RegionOptionsPatch;
import network.foodsharing.api.model.RegionPermissionGrant;
import network.foodsharing.api.model.RegionPin;
import network.foodsharing.api.model.RegionRef;
import network.foodsharing.api.model.RegionSettings;
import network.foodsharing.api.model.RegionSettingsPatch;
import network.foodsharing.api.model.RegionStatistics;
import network.foodsharing.api.model.RegionSuggestion;
import network.foodsharing.api.model.RegionsImportPreview;
import network.foodsharing.api.model.UnitMembership;
import network.foodsharing.api.model.UnitType;
import network.foodsharing.api.model.WorkingGroupFunction;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.PublicRegionService;
import network.foodsharing.region.service.RegionAdminService;
import network.foodsharing.region.service.RegionMembersService;
import network.foodsharing.region.service.RegionOptionsService;
import network.foodsharing.region.service.RegionReadService;
import network.foodsharing.region.service.RegionSettingsService;
import network.foodsharing.region.service.RegionStatisticsService;
import network.foodsharing.region.service.RegionSuggestionService;
import network.foodsharing.region.service.RegionsCsvImportService;
import network.foodsharing.region.service.UpcomingIntroPickups;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/** Region entities and service views ↔ generated models. */
@Component
public class RegionMapper {

    private final UserMapper users;

    public RegionMapper(UserMapper users) {
        this.users = users;
    }

    public RegionRef toRef(Region region) {
        if (region == null) {
            return null;
        }
        RegionRef ref = new RegionRef();
        ref.setId(region.getId());
        ref.setSlug(region.getSlug());
        ref.setName(region.getName());
        ref.setType(UnitType.fromValue(region.getType().name()));
        ref.setParentId(region.getParentId());
        return ref;
    }

    public GroupRef toGroupRef(Region group, network.foodsharing.security.WorkingGroupFunction function) {
        if (group == null) {
            return null;
        }
        GroupRef ref = new GroupRef();
        ref.setId(group.getId());
        ref.setSlug(group.getSlug());
        ref.setName(group.getName());
        ref.setFunction(function(function));
        return ref;
    }

    public UnitMembership toUnit(MembershipService.Unit unit) {
        Region region = unit.region();
        UnitMembership membership = new UnitMembership();
        membership.setId(region.getId());
        membership.setName(region.getName());
        membership.setSlug(region.getSlug());
        membership.setType(UnitType.fromValue(region.getType().name()));
        membership.setParentId(region.getParentId());
        membership.setMember(unit.member());
        membership.setAdmin(unit.admin());
        membership.setHomeRegion(unit.homeRegion());
        membership.setFunction(function(unit.function()));
        return membership;
    }

    public RegionDetail toDetail(RegionReadService.RegionDetailView view) {
        Region region = view.region();
        RegionDetail detail = new RegionDetail();
        detail.setId(region.getId());
        detail.setSlug(region.getSlug());
        detail.setName(region.getName());
        detail.setType(UnitType.fromValue(region.getType().name()));
        detail.setParentId(region.getParentId());
        detail.setParent(toRef(view.parent()));
        detail.setTeaser(region.getTeaser());
        detail.setDescription(region.getDescription());
        detail.setPhotoUrl(region.getPhotoUrl());
        detail.setEmailName(region.getEmailName());
        detail.setTimeZone(region.getTimeZone());
        detail.setCountry(region.getCountry());
        detail.setJoinMode(RegionJoinMode.fromValue(region.getJoinMode().name()));
        detail.setModerated(region.isModerated());
        detail.setHasChildren(region.isHasChildren());
        detail.setMemberCount(view.memberCount());
        detail.setAmbassadors(view.ambassadors().stream().map(users::toSummary).toList());
        detail.setWelcomeTeam(toGroupRef(view.welcomeTeam(), network.foodsharing.security.WorkingGroupFunction.WELCOME));
        detail.setFunctions(view.functions().stream().map(this::toAssignment).toList());
        detail.setConferenceUrl(view.conferenceUrl());
        RegionMembershipState membership = new RegionMembershipState();
        membership.setMember(view.membership().member());
        membership.setAdmin(view.membership().admin());
        membership.setHomeRegion(view.membership().homeRegion());
        membership.setJoinedAt(TimeMapper.toOffset(view.membership().joinedAt()));
        detail.setMembership(membership);
        detail.setStatistics(view.statistics() == null ? null : toStatistics(view.statistics()));
        return detail;
    }

    public RegionFunctionAssignment toAssignment(RegionReadService.FunctionGroup functionGroup) {
        RegionFunctionAssignment assignment = new RegionFunctionAssignment();
        assignment.setFunction(function(functionGroup.function()));
        assignment.setGroup(toGroupRef(functionGroup.group(), functionGroup.function()));
        return assignment;
    }

    public RegionStatistics toStatistics(RegionStatisticsService.StatisticsView view) {
        var cached = view.cached();
        RegionStatistics statistics = new RegionStatistics();
        statistics.setLastModified(TimeMapper.toOffset(cached.getLastModified()));
        statistics.setActiveHomeRegionFoodsavers(cached.getActiveHomeRegionFoodsavers());
        statistics.setActiveCooperations(cached.getActiveCooperations());
        statistics.setPickupsLastMonth(cached.getPickupsLastMonth());
        statistics.setSavedFoodWeightLastMonth(cached.getSavedFoodWeightLastMonth());
        statistics.setActiveFoodSharePoints(cached.getActiveFoodSharePoints());
        statistics.setFoodBasketsLastMonth(cached.getFoodBasketsLastMonth());
        statistics.setMemberCount(cached.getMemberCount());
        statistics.setGenderCounts(new LinkedHashMap<>(view.genderCounts()));
        statistics.setAgeBands(view.ageBands().stream().map(band -> {
            AgeBand ageBand = new AgeBand();
            ageBand.setLabel(band.label());
            ageBand.setCount(band.count());
            return ageBand;
        }).toList());
        return statistics;
    }

    public RegionMember toMember(RegionMembersService.MemberView view) {
        RegionMember member = new RegionMember();
        member.setUser(users.toSummary(view.user(), view.showPhone()));
        member.setAdmin(view.admin());
        member.setJoinedAt(TimeMapper.toOffset(view.membership().getJoinedAt()));
        member.setLastPickupAt(null);
        member.setPermissions(view.permissions().stream().sorted()
                .map(permission -> Permission.fromValue(permission.name())).toList());
        member.setVerified(view.user().isVerified());
        member.setHomeRegion(view.homeRegion());
        return member;
    }

    public RegionPermissionGrant toGrant(RegionMembersService.GrantView view) {
        RegionPermissionGrant grant = new RegionPermissionGrant();
        grant.setUser(users.toSummary(view.user()));
        grant.setPermission(Permission.fromValue(view.permission().name()));
        grant.setSource(view.source());
        grant.setGrantedBy(view.grantedBy() == null ? null : users.toSummary(view.grantedBy()));
        grant.setGrantedAt(TimeMapper.toOffset(view.grantedAt()));
        return grant;
    }

    public RegionOptions toOptions(RegionOptionsService.Options options) {
        RegionOptions model = new RegionOptions();
        model.setEnableReportButton(options.enableReportButton());
        model.setEnableMediationButton(options.enableMediationButton());
        model.setPickupRule(toRule(options.pickupRule()));
        model.setAllowHidingInForum(options.allowHidingInForum());
        model.setReportReasonOptions(options.reportReasonOptions());
        model.setReportReasonOther(options.reportReasonOther());
        model.setNotifyAddressChange(options.notifyAddressChange());
        return model;
    }

    public RegionOptionsService.OptionsChange toChange(RegionOptionsPatch patch) {
        PickupRule rule = patch.getPickupRule();
        return new RegionOptionsService.OptionsChange(patch.getEnableReportButton(), patch.getEnableMediationButton(),
                rule == null ? null : new RegionOptionsService.PickupRule(rule.getActive(), rule.getTimespanDays(),
                        rule.getLimitNumber(), rule.getLimitDayNumber(), rule.getInactiveHours()),
                patch.getAllowHidingInForum(), patch.getReportReasonOptions(), patch.getReportReasonOther(),
                patch.getNotifyAddressChange(), patch.getReason());
    }

    public RegionOptionPermissions toOptionPermissions(RegionOptionsService.OptionPermissions permissions) {
        RegionOptionPermissions model = new RegionOptionPermissions();
        model.setMayEditReportButtons(permissions.mayEditReportButtons());
        model.setMayEditPickupRule(permissions.mayEditPickupRule());
        model.setMayEditUserRelated(permissions.mayEditUserRelated());
        model.setMayEditPin(permissions.mayEditPin());
        return model;
    }

    public RegionPin toPin(RegionOptionsService.Pin pin) {
        if (pin == null) {
            return null;
        }
        RegionPin model = new RegionPin();
        model.setLatitude(pin.latitude());
        model.setLongitude(pin.longitude());
        model.setDescription(pin.description());
        model.setActive(pin.active());
        return model;
    }

    public RegionOptionsService.Pin fromPin(RegionPin pin) {
        return new RegionOptionsService.Pin(pin.getLatitude(), pin.getLongitude(), pin.getDescription(),
                Boolean.TRUE.equals(pin.getActive()));
    }

    public RegionSettings toSettings(RegionSettingsService.Settings settings) {
        RegionSettings model = new RegionSettings();
        var intro = settings.introPickups();
        IntroPickupRegionSettings introModel = new IntroPickupRegionSettings();
        introModel.setMaxActiveBookingsPerUser(intro.maxActiveBookingsPerUser());
        introModel.setPreventDuplicateGuideBookings(intro.preventDuplicateGuideBookings());
        introModel.setPreventDuplicateCategoryBookings(intro.preventDuplicateCategoryBookings());
        introModel.setRequireFairteilerVisitByThirdIntroPickup(intro.requireFairteilerVisitByThirdIntroPickup());
        List<IntroPickupCategory> categories = new ArrayList<>();
        for (String category : intro.ignoredCategories()) {
            try {
                categories.add(IntroPickupCategory.fromValue(category));
            } catch (IllegalArgumentException ignored) {
                // unknown names in the database are not exposed
            }
        }
        introModel.setIgnoredCategories(categories);
        introModel.setCleaningStoreId(intro.cleaningStoreId());
        model.setIntroPickups(introModel);
        OnboardingRegionSettings onboarding = new OnboardingRegionSettings();
        onboarding.setLearningPagesMandatory(settings.onboarding().learningPagesMandatory());
        onboarding.setExtraSteps(settings.onboarding().extraSteps().stream().map(step -> {
            RegionOnboardingStep model2 = new RegionOnboardingStep();
            model2.setId(step.id());
            model2.setKey(step.key());
            model2.setTitles(new LinkedHashMap<>(step.titles()));
            model2.setDescriptions(new LinkedHashMap<>(step.descriptions()));
            model2.setSortOrder(step.sortOrder());
            return model2;
        }).toList());
        model.setOnboarding(onboarding);
        model.setJoinMode(RegionJoinMode.fromValue(settings.joinMode().name()));
        model.setTimeZone(settings.timeZone());
        model.setConferenceUrl(settings.conferenceUrl());
        return model;
    }

    public RegionSettingsService.SettingsChange toChange(RegionSettingsPatch patch) {
        IntroPickupRegionSettings intro = patch.getIntroPickups();
        OnboardingRegionSettings onboarding = patch.getOnboarding();
        return new RegionSettingsService.SettingsChange(
                intro == null ? null : new RegionSettingsService.IntroPickupSettings(intro.getMaxActiveBookingsPerUser(),
                        intro.getPreventDuplicateGuideBookings(), intro.getPreventDuplicateCategoryBookings(),
                        intro.getRequireFairteilerVisitByThirdIntroPickup(),
                        intro.getIgnoredCategories() == null ? Set.of() : intro.getIgnoredCategories().stream()
                                .map(IntroPickupCategory::getValue).collect(Collectors.toCollection(LinkedHashSet::new)),
                        intro.getCleaningStoreId()),
                onboarding == null ? null : new RegionSettingsService.OnboardingSettings(
                        onboarding.getLearningPagesMandatory(),
                        onboarding.getExtraSteps() == null ? null : onboarding.getExtraSteps().stream()
                                .map(step -> new RegionSettingsService.Step(step.getId(), step.getKey(),
                                        step.getTitles(), step.getDescriptions(),
                                        step.getSortOrder() == null ? 0 : step.getSortOrder())).toList()),
                patch.getJoinMode() == null ? null
                        : network.foodsharing.region.domain.RegionJoinMode.valueOf(patch.getJoinMode().name()),
                patch.getTimeZone(), patch.getConferenceUrl(), patch.getReason());
    }

    public RegionAdminItem toAdminItem(RegionAdminService.AdminItem item) {
        Region region = item.region();
        RegionAdminItem model = new RegionAdminItem();
        model.setRegion(toRef(region));
        model.setChildCount((int) item.childCount());
        model.setMemberCount((int) item.memberCount());
        model.setAmbassadors(item.ambassadors().stream().map(users::toSummary).toList());
        model.setFunctions(item.functions().stream().map(this::toAssignment).toList());
        model.setEmailName(region.getEmailName());
        model.setTimeZone(region.getTimeZone());
        model.setCountry(region.getCountry());
        model.setJoinMode(RegionJoinMode.fromValue(region.getJoinMode().name()));
        model.setModerated(region.isModerated());
        model.setTeaser(region.getTeaser());
        model.setDescription(region.getDescription());
        return model;
    }

    public RegionsImportPreview toPreview(RegionsCsvImportService.Preview preview) {
        RegionsImportPreview model = new RegionsImportPreview();
        model.setRows(preview.rows().stream().map(row -> {
            RegionImportRow r = new RegionImportRow();
            r.setLine(row.line());
            r.setParentSlug(row.parentSlug().isEmpty() ? null : row.parentSlug());
            r.setSlug(row.slug());
            r.setName(row.name());
            r.setType(row.type() == null ? null : UnitType.fromValue(row.type().name()));
            r.setTimeZone(row.timeZone());
            r.setCountry(row.country());
            r.setPostcodes(row.postcodes());
            r.setStatus(RegionImportStatus.fromValue(row.status().name()));
            r.setErrors(row.errors());
            return r;
        }).toList());
        model.setCreated(preview.created());
        model.setUpdated(preview.updated());
        model.setErrors(preview.errors());
        model.setDryRun(preview.dryRun());
        return model;
    }

    public PublicRegion toPublic(PublicRegionService.PublicRegionView view) {
        Region region = view.region();
        PublicRegion model = new PublicRegion();
        model.setId(region.getId());
        model.setSlug(region.getSlug());
        model.setName(region.getName());
        model.setType(UnitType.fromValue(region.getType().name()));
        model.setTeaser(region.getTeaser());
        model.setDescription(region.getDescription());
        model.setPhotoUrl(region.getPhotoUrl());
        model.setContactEmail(view.contactEmail());
        model.setAmbassadorNames(view.ambassadorNames());
        model.setChildren(view.children().stream().map(this::toRef).toList());
        model.setParent(toRef(view.parent()));
        model.setMemberCount((int) view.memberCount());
        model.setStoreCount(0);
        model.setNextIntroPickups(view.nextIntroPickups().stream().map(this::toPublicIntroPickup).toList());
        model.setPin(toPin(view.pin()));
        model.setTimeZone(region.getTimeZone());
        return model;
    }

    public PublicIntroPickup toPublicIntroPickup(UpcomingIntroPickups.PublicIntroPickupView view) {
        PublicIntroPickup model = new PublicIntroPickup();
        model.setId(view.id());
        model.setCategory(IntroPickupCategory.fromValue(view.category()));
        model.setStartAt(TimeMapper.toOffset(view.startAt()));
        model.setPublicGeneralLocation(view.publicGeneralLocation());
        model.setBusinessName(view.businessName());
        model.setPublicInfo(view.publicInfo());
        model.setVisitFairteiler(view.visitFairteiler());
        model.setFreeSlots(view.freeSlots());
        model.setRegionSlug(view.regionSlug());
        return model;
    }

    public PublicRegionNode toNode(PublicRegionService.Node node) {
        PublicRegionNode model = new PublicRegionNode();
        model.setId(node.region().getId());
        model.setSlug(node.region().getSlug());
        model.setName(node.region().getName());
        model.setType(UnitType.fromValue(node.region().getType().name()));
        model.setParentId(node.region().getParentId());
        model.setActive(node.active());
        model.setChildren(node.children().stream().map(this::toNode).toList());
        return model;
    }

    public RegionSuggestion toSuggestion(RegionSuggestionService.Suggestion suggestion) {
        RegionSuggestion model = new RegionSuggestion();
        model.setRegion(toRef(suggestion.region()));
        model.setDistanceKm(suggestion.distanceKm());
        model.setMatchedBy(RegionMatch.fromValue(suggestion.matchedBy().name()));
        model.setAmbassadorCount(suggestion.ambassadorCount());
        return model;
    }

    private static PickupRule toRule(RegionOptionsService.PickupRule rule) {
        PickupRule model = new PickupRule();
        model.setActive(rule.active());
        model.setTimespanDays(rule.timespanDays());
        model.setLimitNumber(rule.limitNumber());
        model.setLimitDayNumber(rule.limitDayNumber());
        model.setInactiveHours(rule.inactiveHours());
        return model;
    }

    public static WorkingGroupFunction function(network.foodsharing.security.WorkingGroupFunction function) {
        return function == null ? null : WorkingGroupFunction.fromValue(function.name());
    }

    public static network.foodsharing.security.WorkingGroupFunction function(WorkingGroupFunction function) {
        return function == null ? null : network.foodsharing.security.WorkingGroupFunction.valueOf(function.name());
    }
}
