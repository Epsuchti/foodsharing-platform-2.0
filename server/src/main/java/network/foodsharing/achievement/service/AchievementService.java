package network.foodsharing.achievement.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.achievement.domain.Achievement;
import network.foodsharing.achievement.domain.AchievementAction;
import network.foodsharing.achievement.domain.AchievementI18n;
import network.foodsharing.achievement.domain.AchievementSystemKey;
import network.foodsharing.achievement.domain.AchievementVisibility;
import network.foodsharing.achievement.domain.AwardedAchievement;
import network.foodsharing.achievement.domain.DuplicateMode;
import network.foodsharing.achievement.repository.AchievementI18nRepository;
import network.foodsharing.achievement.repository.AchievementRepository;
import network.foodsharing.achievement.repository.AwardedAchievementRepository;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Achievements (AC1): definitions per region or global, awards with validity and duplicate mode, visibility per
 * viewer ({@link AchievementRules}). Every change is ACHIEVEMENT history; awards send {@code ACHIEVEMENT_AWARDED}.
 */
@Service
public class AchievementService {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final String DEFAULT_LANGUAGE = "de";

    /**
     * Create / update input; on update null means unchanged ({@code translations} null = unchanged) and
     * {@code clearValidityDays} sets the validity back to unlimited.
     */
    public record AchievementInput(UUID regionId, String name, String description, String icon, Integer validityDays,
                                   AchievementVisibility visibility, DuplicateMode duplicateMode,
                                   Map<String, Text> translations, boolean clearValidityDays) {

        public AchievementInput(UUID regionId, String name, String description, String icon, Integer validityDays,
                                AchievementVisibility visibility, DuplicateMode duplicateMode,
                                Map<String, Text> translations) {
            this(regionId, name, description, icon, validityDays, visibility, duplicateMode, translations, false);
        }
    }

    public record Text(String name, String description) {
    }

    public record AchievementView(Achievement achievement, String name, String description,
                                  Map<String, Text> translations, int awardedCount, boolean mayAdminister) {
    }

    public record AwardView(AwardedAchievement award, Achievement achievement, String name, String regionName) {
    }

    private final AchievementRepository achievements;
    private final AchievementI18nRepository texts;
    private final AwardedAchievementRepository awards;
    private final AchievementPermissionService access;
    private final CurrentActorService actors;
    private final UserService users;
    private final RegionService regions;
    private final HistoryService history;
    private final NotificationService notifications;
    private final Clock clock;

    public AchievementService(AchievementRepository achievements, AchievementI18nRepository texts,
                              AwardedAchievementRepository awards, AchievementPermissionService access,
                              CurrentActorService actors, UserService users, RegionService regions,
                              HistoryService history, NotificationService notifications, Clock clock) {
        this.achievements = achievements;
        this.texts = texts;
        this.awards = awards;
        this.access = access;
        this.actors = actors;
        this.users = users;
        this.regions = regions;
        this.history = history;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------------------------- definitions

    /** @throws ApiException 403 when the actor is neither member nor administrator of the region */
    @Transactional(readOnly = true)
    public List<AchievementView> list(UUID regionId) {
        SessionPrincipal actor = actors.requirePrincipal();
        if (regionId != null) {
            regions.require(regionId);
        }
        AchievementRules.Viewer viewer = access.viewer(actor, regionId, null);
        if (!viewer.admin() && !viewer.member()) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        List<Achievement> list = regionId == null ? achievements.findByRegionIdIsNullAndDeletedAtIsNullOrderByNameAsc()
                : achievements.findByRegionIdAndDeletedAtIsNullOrderByNameAsc(regionId);
        return views(list.stream().filter(a -> AchievementRules.definitionVisible(a.getVisibility(), viewer))
                .toList(), actor, viewer.admin());
    }

    /** @throws ApiException 404 ACHIEVEMENT_NOT_FOUND, 403 */
    @Transactional(readOnly = true)
    public AchievementView get(UUID achievementId) {
        SessionPrincipal actor = actors.requirePrincipal();
        Achievement achievement = requireLive(achievementId);
        AchievementRules.Viewer viewer = access.viewer(actor, achievement.getRegionId(), null);
        if (!AchievementRules.definitionVisible(achievement.getVisibility(), viewer)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return views(List.of(achievement), actor, viewer.admin()).getFirst();
    }

    /** @throws ApiException 403, 400 VALIDATION_FAILED, 404 REGION_NOT_FOUND */
    @Transactional
    public AchievementView create(AchievementInput input) {
        SessionPrincipal actor = actors.requirePrincipal();
        if (input.regionId() != null) {
            regions.require(input.regionId());
        }
        access.requireAdminister(actor, input.regionId());
        if (blank(input.name()) || blank(input.icon()) || input.visibility() == null
                || input.duplicateMode() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Achievement achievement = new Achievement();
        achievement.setRegionId(input.regionId());
        apply(achievement, input);
        Achievement saved = achievements.save(achievement);
        if (input.translations() != null) {
            saveTranslations(saved.getId(), input.translations());
        }
        history.record(history.event(AchievementAction.DEFINED, saved.getId()).region(saved.getRegionId())
                .metadata("name", saved.getName()).build());
        return views(List.of(saved), actor, true).getFirst();
    }

    /** @throws ApiException 404 ACHIEVEMENT_NOT_FOUND, 403, 400 VALIDATION_FAILED */
    @Transactional
    public AchievementView update(UUID achievementId, AchievementInput input) {
        SessionPrincipal actor = actors.requirePrincipal();
        Achievement achievement = requireLive(achievementId);
        access.requireAdminister(actor, achievement.getRegionId());
        if (input.name() != null && blank(input.name()) || input.icon() != null && blank(input.icon())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (input.clearValidityDays() && input.validityDays() != null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "clearValidityDays");
        }
        String oldName = achievement.getName();
        AchievementVisibility oldVisibility = achievement.getVisibility();
        Integer oldValidity = achievement.getValidityDays();
        apply(achievement, input);
        if (input.clearValidityDays()) {
            achievement.setValidityDays(null);
        }
        if (input.translations() != null) {
            saveTranslations(achievementId, input.translations());
        }
        history.record(history.event(AchievementAction.UPDATED, achievementId).region(achievement.getRegionId())
                .change("name", oldName, achievement.getName())
                .metadata("validityDays", Objects.toString(achievement.getValidityDays(), ""))
                .metadata("previousValidityDays", Objects.toString(oldValidity, ""))
                .metadata("visibility", achievement.getVisibility().name())
                .metadata("previousVisibility", oldVisibility.name()).build());
        return views(List.of(achievement), actor, true).getFirst();
    }

    /** Soft delete; awards stay. @throws ApiException 404, 403, 409 ACHIEVEMENT_SYSTEM_PROTECTED */
    @Transactional
    public void delete(UUID achievementId) {
        SessionPrincipal actor = actors.requirePrincipal();
        Achievement achievement = requireLive(achievementId);
        access.requireAdminister(actor, achievement.getRegionId());
        if (achievement.getSystemKey() != null) {
            throw ApiException.conflict(ApiErrorCode.ACHIEVEMENT_SYSTEM_PROTECTED);
        }
        achievement.setDeletedAt(clock.instant());
        history.record(history.event(AchievementAction.DELETED, achievementId).region(achievement.getRegionId())
                .metadata("name", achievement.getName()).build());
    }

    // --------------------------------------------------------------------------------------------- awards

    /** All awards (incl. revoked) of an achievement; administrators only. */
    @Transactional(readOnly = true)
    public List<AwardView> awards(UUID achievementId) {
        SessionPrincipal actor = actors.requirePrincipal();
        Achievement achievement = requireLive(achievementId);
        access.requireAdminister(actor, achievement.getRegionId());
        return awardViews(awards.findByAchievementIdOrderByAwardedAtDesc(achievementId), actor);
    }

    /**
     * Awards {@code achievementId} to {@code userId}. OVERRIDE extends the member's active award instead of adding a
     * second one (upstream {@code AchievementTransactions.awardAchievement}).
     *
     * @throws ApiException 404 ACHIEVEMENT_NOT_FOUND / USER_NOT_FOUND, 403 ACCESS_DENIED (not administrator, self),
     *                      403 ACHIEVEMENT_RECIPIENT_NOT_MEMBER
     */
    @Transactional
    public AwardView award(UUID achievementId, UUID userId, LocalDate validUntil, String notice) {
        SessionPrincipal actor = actors.requirePrincipal();
        Achievement achievement = requireLive(achievementId);
        if (!AchievementRules.mayAward(access.mayAdminister(actor, achievement),
                actor.userId().equals(userId))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        User recipient = users.findActive(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        if (!access.isActiveMember(recipient.getId(), achievement.getRegionId())) {
            throw ApiException.forbidden(ApiErrorCode.ACHIEVEMENT_RECIPIENT_NOT_MEMBER);
        }
        AwardedAchievement award = grant(achievement, recipient.getId(), actor.userId(), validUntil, notice, false);
        return awardViews(List.of(award), actor).getFirst();
    }

    /**
     * {@code clearValidUntil} makes the award valid without end; the change is in the AWARD_EDITED history.
     *
     * @throws ApiException 404 AWARD_NOT_FOUND, 403, 400 VALIDATION_FAILED when clearing and setting at once
     */
    @Transactional
    public AwardView updateAward(UUID awardId, LocalDate validUntil, boolean clearValidUntil, String notice) {
        SessionPrincipal actor = actors.requirePrincipal();
        if (clearValidUntil && validUntil != null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "clearValidUntil");
        }
        AwardedAchievement award = requireAward(awardId);
        Achievement achievement = achievements.findById(award.getAchievementId()).orElseThrow();
        access.requireAdminister(actor, achievement.getRegionId());
        LocalDate oldValid = award.getValidUntil();
        if (validUntil != null || clearValidUntil) {
            award.setValidUntil(validUntil);
        }
        if (notice != null) {
            award.setNotice(notice.isBlank() ? null : notice.strip());
        }
        history.record(history.event(AchievementAction.AWARD_EDITED, achievement.getId())
                .secondarySubject(award.getUserId()).region(achievement.getRegionId())
                .change("validUntil", oldValid, award.getValidUntil()).metadata("awardId", awardId.toString())
                .build());
        return awardViews(List.of(award), actor).getFirst();
    }

    /** Soft revoke. @throws ApiException 404 AWARD_NOT_FOUND, 403 */
    @Transactional
    public void revoke(UUID awardId) {
        SessionPrincipal actor = actors.requirePrincipal();
        AwardedAchievement award = requireAward(awardId);
        Achievement achievement = achievements.findById(award.getAchievementId()).orElseThrow();
        access.requireAdminister(actor, achievement.getRegionId());
        award.setRevokedAt(clock.instant());
        award.setRevokedBy(actor.userId());
        history.record(history.event(AchievementAction.REVOKED, achievement.getId())
                .secondarySubject(award.getUserId()).region(achievement.getRegionId())
                .metadata("awardId", awardId.toString()).build());
    }

    /** Active awards of a member visible to the current actor. @throws ApiException 404 USER_NOT_FOUND */
    @Transactional(readOnly = true)
    public List<AwardView> userAwards(UUID userId) {
        SessionPrincipal actor = actors.requirePrincipal();
        users.findActive(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        return awardViews(visibleAwards(actor, userId), actor);
    }

    /** Profile SPI: awards of {@code userId} visible to {@code viewer}. */
    @Transactional(readOnly = true)
    public List<AwardView> profileAwards(SessionPrincipal viewer, UUID userId) {
        return awardViews(visibleAwards(viewer, userId), viewer);
    }

    /**
     * Automatic award (actor SYSTEM, history AUTO_AWARDED) of a system achievement; no-op when the achievement is
     * missing or deleted. Uses the achievement's duplicate mode and validity.
     */
    @Transactional
    public Optional<AwardedAchievement> autoAward(AchievementSystemKey key, UUID userId, Integer validityDays) {
        Optional<Achievement> achievement = achievements.findBySystemKey(key).filter(a -> a.getDeletedAt() == null);
        if (achievement.isEmpty() || users.findActive(userId).isEmpty()) {
            return Optional.empty();
        }
        LocalDate validUntil = validityDays == null ? null : today().plusDays(validityDays);
        return Optional.of(grant(achievement.get(), userId, null, validUntil, null, true));
    }

    /** End of the member's active award of a system achievement (latest; empty = none or unlimited unknown). */
    @Transactional(readOnly = true)
    public Optional<AwardedAchievement> activeSystemAward(AchievementSystemKey key, UUID userId) {
        return achievements.findBySystemKey(key).flatMap(a -> awards
                .findByUserIdAndAchievementIdAndRevokedAtIsNullOrderByAwardedAtDesc(userId, a.getId()).stream()
                .findFirst());
    }

    // -------------------------------------------------------------------------------------------- helpers

    private AwardedAchievement grant(Achievement achievement, UUID userId, UUID awardedBy, LocalDate requested,
                                     String notice, boolean automatic) {
        Instant now = clock.instant();
        LocalDate validUntil = AchievementRules.validUntil(today(), achievement.getValidityDays(), requested);
        String text = notice == null || notice.isBlank() ? null : notice.strip();
        AwardedAchievement award = null;
        boolean extended = false;
        if (achievement.getDuplicateMode() == DuplicateMode.OVERRIDE) {
            award = awards.findByUserIdAndAchievementIdAndRevokedAtIsNullOrderByAwardedAtDesc(userId,
                    achievement.getId()).stream().findFirst().orElse(null);
        }
        if (award != null) {
            extended = true;
            award.setValidUntil(automatic ? validUntil : AchievementRules.extended(award.getValidUntil(), validUntil));
            if (text != null) {
                award.setNotice(text);
            }
        } else {
            award = new AwardedAchievement();
            award.setAchievementId(achievement.getId());
            award.setUserId(userId);
            award.setAwardedBy(awardedBy);
            award.setAwardedAt(now);
            award.setValidUntil(validUntil);
            award.setNotice(text);
            award = awards.save(award);
        }
        var event = history.event(automatic ? AchievementAction.AUTO_AWARDED : AchievementAction.AWARDED,
                        achievement.getId()).secondarySubject(userId).region(achievement.getRegionId())
                .metadata("awardId", award.getId().toString()).metadata("extended", extended)
                .metadata("validUntil", Objects.toString(award.getValidUntil(), ""));
        if (automatic) {
            event.actorSystem();
        }
        history.record(event.build());
        notifications.notify(NotificationRequest.to(List.of(userId), NotificationType.ACHIEVEMENT_AWARDED)
                .param("name", achievement.getName()).link("/profile/" + userId)
                .identifier("ACHIEVEMENT_AWARDED-" + award.getId()).icon(achievement.getIcon()).build());
        return award;
    }

    private List<AwardedAchievement> visibleAwards(SessionPrincipal viewer, UUID userId) {
        List<AwardedAchievement> list = awards.findByUserIdAndRevokedAtIsNullOrderByAwardedAtDesc(userId);
        Map<UUID, Achievement> defs = definitions(list.stream().map(AwardedAchievement::getAchievementId).toList());
        Map<UUID, AchievementRules.Viewer> viewers = new HashMap<>();
        return list.stream().filter(award -> {
            Achievement achievement = defs.get(award.getAchievementId());
            if (achievement == null || achievement.getDeletedAt() != null) {
                return false;
            }
            AchievementRules.Viewer facts = viewers.computeIfAbsent(achievement.getId(),
                    id -> access.viewer(viewer, achievement.getRegionId(), userId));
            return AchievementRules.awardVisible(achievement.getVisibility(), facts);
        }).toList();
    }

    private List<AwardView> awardViews(List<AwardedAchievement> list, SessionPrincipal actor) {
        Map<UUID, Achievement> defs = definitions(list.stream().map(AwardedAchievement::getAchievementId).toList());
        String language = language(actor);
        Map<UUID, Map<String, Text>> translations = translations(defs.keySet());
        Map<UUID, String> regionNames = regions.findAll(defs.values().stream().map(Achievement::getRegionId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Region::getId, Region::getName));
        return list.stream().map(award -> {
            Achievement achievement = defs.get(award.getAchievementId());
            Text text = localized(achievement, translations.getOrDefault(achievement.getId(), Map.of()), language);
            return new AwardView(award, achievement, text.name(),
                    achievement.getRegionId() == null ? null : regionNames.get(achievement.getRegionId()));
        }).toList();
    }

    private List<AchievementView> views(List<Achievement> list, SessionPrincipal actor, boolean adminKnown) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Achievement::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : awards.countActive(ids)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        Map<UUID, Map<String, Text>> translations = translations(ids);
        String language = language(actor);
        return list.stream().map(a -> {
            Map<String, Text> own = translations.getOrDefault(a.getId(), Map.of());
            Text text = localized(a, own, language);
            boolean mayAdminister = adminKnown || access.mayAdminister(actor, a);
            return new AchievementView(a, text.name(), text.description(), own,
                    counts.getOrDefault(a.getId(), 0L).intValue(), mayAdminister);
        }).toList();
    }

    private Map<UUID, Achievement> definitions(Collection<UUID> ids) {
        return achievements.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Achievement::getId, Function.identity()));
    }

    private Map<UUID, Map<String, Text>> translations(Collection<UUID> ids) {
        Map<UUID, Map<String, Text>> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (AchievementI18n row : texts.findByOwnerIdIn(ids)) {
            result.computeIfAbsent(row.getOwnerId(), id -> new LinkedHashMap<>())
                    .put(row.getLanguage(), new Text(row.getName(), row.getDescription()));
        }
        return result;
    }

    /** A14: requested language → base text (de). */
    static Text localized(Achievement achievement, Map<String, Text> translations, String language) {
        Text text = DEFAULT_LANGUAGE.equals(language) ? null : translations.get(language);
        if (text == null) {
            return new Text(achievement.getName(), achievement.getDescription());
        }
        return new Text(text.name(), text.description() != null ? text.description() : achievement.getDescription());
    }

    private String language(SessionPrincipal actor) {
        return actor == null ? DEFAULT_LANGUAGE : users.findActive(actor.userId())
                .map(u -> u.getPreferredLanguage().getValue()).orElse(DEFAULT_LANGUAGE);
    }

    private void saveTranslations(UUID ownerId, Map<String, Text> wanted) {
        Map<String, AchievementI18n> existing = texts.findByOwnerId(ownerId).stream()
                .collect(Collectors.toMap(AchievementI18n::getLanguage, Function.identity()));
        existing.values().stream().filter(row -> !wanted.containsKey(row.getLanguage())).forEach(texts::delete);
        wanted.forEach((language, text) -> {
            if (DEFAULT_LANGUAGE.equals(language) || text == null || blank(text.name())) {
                return;
            }
            AchievementI18n row = existing.computeIfAbsent(language, l -> {
                AchievementI18n fresh = new AchievementI18n();
                fresh.setOwnerId(ownerId);
                fresh.setLanguage(l);
                return fresh;
            });
            row.setName(text.name().strip());
            row.setDescription(text.description() == null || text.description().isBlank() ? null
                    : text.description().strip());
            texts.save(row);
        });
    }

    private void apply(Achievement achievement, AchievementInput input) {
        if (input.name() != null) {
            achievement.setName(input.name().strip());
        }
        if (input.description() != null) {
            achievement.setDescription(input.description().isBlank() ? null : input.description().strip());
        }
        if (input.icon() != null) {
            achievement.setIcon(input.icon().strip());
        }
        if (input.validityDays() != null) {
            achievement.setValidityDays(input.validityDays());
        }
        if (input.visibility() != null) {
            achievement.setVisibility(input.visibility());
        }
        if (input.duplicateMode() != null) {
            achievement.setDuplicateMode(input.duplicateMode());
        }
    }

    private Achievement requireLive(UUID achievementId) {
        return achievements.findById(achievementId).filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.ACHIEVEMENT_NOT_FOUND));
    }

    private AwardedAchievement requireAward(UUID awardId) {
        return awards.findById(awardId).filter(AwardedAchievement::isActive)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.AWARD_NOT_FOUND));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZURICH);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
