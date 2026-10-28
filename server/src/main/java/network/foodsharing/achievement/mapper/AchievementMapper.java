package network.foodsharing.achievement.mapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import network.foodsharing.achievement.domain.AchievementVisibility;
import network.foodsharing.achievement.domain.DuplicateMode;
import network.foodsharing.achievement.service.AchievementService;
import network.foodsharing.api.model.Achievement;
import network.foodsharing.api.model.AchievementCreate;
import network.foodsharing.api.model.AchievementSystemKey;
import network.foodsharing.api.model.AchievementTranslation;
import network.foodsharing.api.model.AchievementUpdate;
import network.foodsharing.api.model.Award;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Achievement entities ↔ API models (AC1). */
@Component
public class AchievementMapper {

    private final UserService users;
    private final UserMapper userMapper;

    public AchievementMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public AchievementService.AchievementInput toInput(AchievementCreate in) {
        return new AchievementService.AchievementInput(in.getRegionId(), in.getName(), in.getDescription(),
                in.getIcon(), in.getValidityDays(), AchievementVisibility.valueOf(in.getVisibility().getValue()),
                DuplicateMode.valueOf(in.getDuplicateMode().getValue()), texts(in.getTranslations()));
    }

    public AchievementService.AchievementInput toInput(AchievementUpdate in) {
        return new AchievementService.AchievementInput(null, in.getName(), in.getDescription(), in.getIcon(),
                in.getValidityDays(),
                in.getVisibility() == null ? null : AchievementVisibility.valueOf(in.getVisibility().getValue()),
                in.getDuplicateMode() == null ? null : DuplicateMode.valueOf(in.getDuplicateMode().getValue()),
                texts(in.getTranslations()));
    }

    public Achievement toAchievement(AchievementService.AchievementView view) {
        var a = view.achievement();
        Map<String, AchievementTranslation> translations = new LinkedHashMap<>();
        view.translations().forEach((language, text) -> translations.put(language,
                new AchievementTranslation().name(text.name()).description(text.description())));
        return new Achievement().id(a.getId()).regionId(a.getRegionId()).name(view.name())
                .description(view.description()).icon(a.getIcon()).validityDays(a.getValidityDays())
                .visibility(network.foodsharing.api.model.AchievementVisibility.fromValue(a.getVisibility().name()))
                .duplicateMode(network.foodsharing.api.model.DuplicateMode.fromValue(a.getDuplicateMode().name()))
                .systemKey(a.getSystemKey() == null ? null : AchievementSystemKey.fromValue(a.getSystemKey().name()))
                .awardedCount(view.awardedCount()).mayAdminister(view.mayAdminister())
                .translations(translations.isEmpty() ? null : translations);
    }

    public List<Achievement> toAchievements(List<AchievementService.AchievementView> views) {
        return views.stream().map(this::toAchievement).toList();
    }

    public List<Award> toAwards(List<AchievementService.AwardView> views) {
        Map<UUID, UserSummary> people = people(views.stream()
                .flatMap(v -> Stream.of(v.award().getUserId(), v.award().getAwardedBy())).toList());
        return views.stream().map(v -> {
            var award = v.award();
            return new Award().id(award.getId()).achievementId(award.getAchievementId()).achievementName(v.name())
                    .icon(v.achievement().getIcon()).user(people.get(award.getUserId()))
                    .awardedBy(award.getAwardedBy() == null ? null : people.get(award.getAwardedBy()))
                    .awardedAt(TimeMapper.toOffset(award.getAwardedAt())).validUntil(award.getValidUntil())
                    .notice(award.getNotice()).revokedAt(TimeMapper.toOffset(award.getRevokedAt()))
                    .regionName(v.regionName());
        }).toList();
    }

    public Award toAward(AchievementService.AwardView view) {
        return toAwards(List.of(view)).getFirst();
    }

    private static Map<String, AchievementService.Text> texts(Map<String, AchievementTranslation> in) {
        if (in == null) {
            return null;
        }
        Map<String, AchievementService.Text> out = new LinkedHashMap<>();
        in.forEach((language, text) -> {
            if (text != null) {
                out.put(language, new AchievementService.Text(text.getName(), text.getDescription()));
            }
        });
        return out;
    }

    private Map<UUID, UserSummary> people(Collection<UUID> ids) {
        Set<UUID> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return users.findAll(unique).stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a));
    }
}
