package network.foodsharing.region.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.GroupAction;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.domain.RegionJoinMode;
import network.foodsharing.region.domain.RegionOnboardingStep;
import network.foodsharing.region.domain.RegionOnboardingStepI18n;
import network.foodsharing.region.repository.RegionOnboardingStepI18nRepository;
import network.foodsharing.region.repository.RegionOnboardingStepRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region settings (plan §15.3): EinAb booking rules (§14.1 EA22), onboarding settings and extra journey steps
 * (§11.1, translations as A14), join mode, time zone, conference URL. Readable by administrators of the region and
 * CAN_MANAGE_USERS holders, writable by administrators. Intro-pickup categories are kept as names (the enum belongs to
 * the intropickup module).
 */
@Service
public class RegionSettingsService {

    public record IntroPickupSettings(int maxActiveBookingsPerUser, boolean preventDuplicateGuideBookings,
                                      boolean preventDuplicateCategoryBookings,
                                      boolean requireFairteilerVisitByThirdIntroPickup, Set<String> ignoredCategories,
                                      UUID cleaningStoreId) {
    }

    public record Step(UUID id, String key, Map<String, String> titles, Map<String, String> descriptions,
                       int sortOrder) {
    }

    public record OnboardingSettings(boolean learningPagesMandatory, List<Step> extraSteps) {
    }

    public record Settings(IntroPickupSettings introPickups, OnboardingSettings onboarding, RegionJoinMode joinMode,
                           String timeZone, String conferenceUrl) {
    }

    /** null = unchanged. */
    public record SettingsChange(IntroPickupSettings introPickups, OnboardingSettings onboarding,
                                 RegionJoinMode joinMode, String timeZone, String conferenceUrl, String reason) {
    }

    private final RegionService regionService;
    private final RegionOnboardingStepRepository steps;
    private final RegionOnboardingStepI18nRepository stepTexts;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;

    public RegionSettingsService(RegionService regionService, RegionOnboardingStepRepository steps,
                                 RegionOnboardingStepI18nRepository stepTexts, CurrentActorService currentActor,
                                 PermissionService permissions, HistoryService history) {
        this.regionService = regionService;
        this.steps = steps;
        this.stepTexts = stepTexts;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public Settings settings(UUID regionId) {
        Region region = regionService.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.has(actor, Permission.CAN_MANAGE_USERS, regionId)) {
            permissions.requireMayAdministrateRegion(actor, regionId);
        }
        return read(region);
    }

    /** Settings without permission check (other modules: booking rules, onboarding steps). */
    @Transactional(readOnly = true)
    public Settings read(Region region) {
        IntroPickupSettings intro = new IntroPickupSettings(region.getMaxActiveBookingsPerUser(),
                region.isPreventDuplicateGuideBookings(), region.isPreventDuplicateCategoryBookings(),
                region.isRequireFairteilerVisitByThird(), categories(region.getIgnoredCategories()),
                region.getCleaningStoreId());
        return new Settings(intro, new OnboardingSettings(region.isLearningPagesMandatory(), steps(region.getId())),
                region.getJoinMode(), region.getTimeZone(), region.getConferenceUrl());
    }

    /** Extra onboarding steps of a region with their translations, by sort order. */
    @Transactional(readOnly = true)
    public List<Step> steps(UUID regionId) {
        List<RegionOnboardingStep> rows = steps.findByRegionIdOrderBySortOrderAsc(regionId);
        Map<UUID, List<RegionOnboardingStepI18n>> texts = stepTexts.findByOwnerIdIn(
                rows.stream().map(RegionOnboardingStep::getId).toList()).stream()
                .collect(Collectors.groupingBy(RegionOnboardingStepI18n::getOwnerId));
        List<Step> result = new ArrayList<>();
        for (RegionOnboardingStep row : rows) {
            Map<String, String> titles = new LinkedHashMap<>();
            Map<String, String> descriptions = new LinkedHashMap<>();
            for (RegionOnboardingStepI18n text : texts.getOrDefault(row.getId(), List.of())) {
                titles.put(text.getLanguage(), text.getTitle());
                if (text.getDescription() != null) {
                    descriptions.put(text.getLanguage(), text.getDescription());
                }
            }
            result.add(new Step(row.getId(), row.getStepKey(), titles, descriptions, row.getSortOrder()));
        }
        return result;
    }

    /**
     * Changes settings; one SETTINGS_CHANGED entry per changed field.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED, 400 INVALID_TIME_ZONE / VALIDATION_FAILED
     */
    @Transactional
    public Settings update(UUID regionId, SettingsChange change) {
        Region region = regionService.require(regionId);
        permissions.requireMayAdministrateRegion(currentActor.requirePrincipal(), regionId);
        HistoryAction action = region.isGroup() ? GroupAction.UPDATED : RegionAction.SETTINGS_CHANGED;
        if (change.timeZone() != null) {
            if (!RegionService.isValidZone(change.timeZone())) {
                throw ApiException.badRequest(ApiErrorCode.INVALID_TIME_ZONE).withArg("timeZone", change.timeZone());
            }
            record(action, region, "timeZone", region.getTimeZone(), change.timeZone(), change.reason());
            region.setTimeZone(change.timeZone());
        }
        if (change.joinMode() != null) {
            record(action, region, "joinMode", region.getJoinMode(), change.joinMode(), change.reason());
            region.setJoinMode(change.joinMode());
        }
        if (change.conferenceUrl() != null) {
            String url = change.conferenceUrl().isBlank() ? null : change.conferenceUrl().strip();
            record(action, region, "conferenceUrl", region.getConferenceUrl(), url, change.reason());
            region.setConferenceUrl(url);
        }
        IntroPickupSettings intro = change.introPickups();
        if (intro != null) {
            record(action, region, "maxActiveBookingsPerUser", region.getMaxActiveBookingsPerUser(),
                    intro.maxActiveBookingsPerUser(), change.reason());
            region.setMaxActiveBookingsPerUser(intro.maxActiveBookingsPerUser());
            record(action, region, "preventDuplicateGuideBookings", region.isPreventDuplicateGuideBookings(),
                    intro.preventDuplicateGuideBookings(), change.reason());
            region.setPreventDuplicateGuideBookings(intro.preventDuplicateGuideBookings());
            record(action, region, "preventDuplicateCategoryBookings", region.isPreventDuplicateCategoryBookings(),
                    intro.preventDuplicateCategoryBookings(), change.reason());
            region.setPreventDuplicateCategoryBookings(intro.preventDuplicateCategoryBookings());
            record(action, region, "requireFairteilerVisitByThird", region.isRequireFairteilerVisitByThird(),
                    intro.requireFairteilerVisitByThirdIntroPickup(), change.reason());
            region.setRequireFairteilerVisitByThird(intro.requireFairteilerVisitByThirdIntroPickup());
            String ignored = intro.ignoredCategories() == null || intro.ignoredCategories().isEmpty() ? null
                    : intro.ignoredCategories().stream().sorted().collect(Collectors.joining(","));
            record(action, region, "ignoredCategories", region.getIgnoredCategories(), ignored, change.reason());
            region.setIgnoredCategories(ignored);
            record(action, region, "cleaningStoreId", region.getCleaningStoreId(), intro.cleaningStoreId(),
                    change.reason());
            region.setCleaningStoreId(intro.cleaningStoreId());
        }
        OnboardingSettings onboarding = change.onboarding();
        if (onboarding != null) {
            record(action, region, "learningPagesMandatory", region.isLearningPagesMandatory(),
                    onboarding.learningPagesMandatory(), change.reason());
            region.setLearningPagesMandatory(onboarding.learningPagesMandatory());
            if (onboarding.extraSteps() != null) {
                List<String> before = steps(regionId).stream().map(Step::key).toList();
                replaceSteps(regionId, onboarding.extraSteps());
                List<String> after = onboarding.extraSteps().stream().map(Step::key).toList();
                if (!before.equals(after)) {
                    history.record(history.event(action, regionId).region(regionId)
                            .change("onboardingSteps", String.join(",", before), String.join(",", after))
                            .reason(change.reason()).build());
                }
            }
        }
        return read(region);
    }

    private void replaceSteps(UUID regionId, List<Step> requested) {
        Map<UUID, RegionOnboardingStep> existing = steps.findByRegionIdOrderBySortOrderAsc(regionId).stream()
                .collect(Collectors.toMap(RegionOnboardingStep::getId, step -> step));
        Set<String> keys = new java.util.HashSet<>();
        for (Step step : requested) {
            if (step.key() == null || step.key().isBlank() || !keys.add(step.key())
                    || step.titles() == null || step.titles().isEmpty()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("step", String.valueOf(step.key()));
            }
        }
        List<UUID> kept = requested.stream().map(Step::id).filter(Objects::nonNull).toList();
        existing.values().stream().filter(step -> !kept.contains(step.getId())).forEach(step -> {
            stepTexts.deleteByOwnerId(step.getId());
            steps.delete(step);
        });
        steps.flush();
        for (Step step : requested) {
            RegionOnboardingStep row = step.id() == null ? null : existing.get(step.id());
            if (row == null) {
                row = new RegionOnboardingStep();
                row.setRegionId(regionId);
            }
            row.setStepKey(step.key().strip());
            row.setSortOrder(step.sortOrder());
            RegionOnboardingStep saved = steps.saveAndFlush(row);
            stepTexts.deleteByOwnerId(saved.getId());
            stepTexts.flush();
            for (Map.Entry<String, String> title : step.titles().entrySet()) {
                RegionOnboardingStepI18n text = new RegionOnboardingStepI18n();
                text.setOwnerId(saved.getId());
                text.setLanguage(title.getKey());
                text.setTitle(title.getValue());
                text.setDescription(step.descriptions() == null ? null : step.descriptions().get(title.getKey()));
                stepTexts.save(text);
            }
        }
    }

    private void record(HistoryAction action, Region region, String field, Object oldValue, Object newValue,
                        String reason) {
        if (!Objects.equals(oldValue, newValue)) {
            history.record(history.event(action, region.getId()).region(region.getId())
                    .change(field, oldValue, newValue).reason(reason).build());
        }
    }

    static Set<String> categories(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }
}
