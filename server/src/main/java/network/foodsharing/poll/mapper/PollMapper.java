package network.foodsharing.poll.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Poll;
import network.foodsharing.api.model.PollCreate;
import network.foodsharing.api.model.PollOption;
import network.foodsharing.api.model.PollOptionValue;
import network.foodsharing.api.model.PollScope;
import network.foodsharing.api.model.PollScopeCount;
import network.foodsharing.api.model.PollStatus;
import network.foodsharing.api.model.PollSummary;
import network.foodsharing.api.model.PollType;
import network.foodsharing.api.model.PollUpdate;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.poll.service.PollRules;
import network.foodsharing.poll.service.PollViews;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Poll entities and API models. Option values (results) only once the poll ended. */
@Component
public class PollMapper {

    private final UserService users;
    private final UserMapper userMapper;

    public PollMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public static PollViews.PollDraft toDraft(PollCreate create) {
        return new PollViews.PollDraft(create.getRegionId(), create.getName(), create.getDescription(),
                network.foodsharing.poll.domain.PollType.valueOf(create.getType().getValue()),
                network.foodsharing.poll.domain.PollScope.valueOf(create.getScope().getValue()),
                TimeMapper.toInstant(create.getStartsAt()), TimeMapper.toInstant(create.getEndsAt()),
                List.copyOf(create.getOptions()), Boolean.TRUE.equals(create.getShuffleOptions()));
    }

    public static PollViews.PollChange toChange(PollUpdate update) {
        return new PollViews.PollChange(update.getName(), update.getDescription(),
                update.getOptions() == null ? null : List.copyOf(update.getOptions()), update.getShuffleOptions());
    }

    /** @throws ApiException 400 INVALID_VOTE when a key is not an option index */
    public static Map<Integer, Integer> toBallot(Map<String, Integer> values) {
        Map<Integer, Integer> ballot = new HashMap<>();
        if (values == null) {
            return ballot;
        }
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            try {
                ballot.put(Integer.parseInt(entry.getKey().strip()), entry.getValue());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest(ApiErrorCode.INVALID_VOTE);
            }
        }
        return ballot;
    }

    public static List<PollScopeCount> toScopeCounts(Map<network.foodsharing.poll.domain.PollScope, Integer> counts) {
        return counts.entrySet().stream().map(e -> new PollScopeCount().scope(PollScope.fromValue(e.getKey().name()))
                .count(e.getValue())).toList();
    }

    public static List<PollSummary> toSummaries(List<PollViews.PollItem> items) {
        return items.stream().map(PollMapper::toSummary).toList();
    }

    public static PollSummary toSummary(PollViews.PollItem item) {
        network.foodsharing.poll.domain.Poll poll = item.poll();
        return new PollSummary().id(poll.getId()).regionId(poll.getRegionId()).name(poll.getName())
                .type(PollType.fromValue(poll.getType().name())).scope(PollScope.fromValue(poll.getScope().name()))
                .startsAt(TimeMapper.toOffset(poll.getStartsAt())).endsAt(TimeMapper.toOffset(poll.getEndsAt()))
                .status(PollStatus.fromValue(item.status().name())).voted(item.voted())
                .eligibleCount(eligibleCount(item)).voteCount(poll.getVoteCount());
    }

    public Poll toPoll(PollViews.PollDetails details) {
        PollViews.PollItem item = details.item();
        network.foodsharing.poll.domain.Poll poll = item.poll();
        PollRules.Actor actor = item.actor();
        Map<Integer, List<PollOptionValue>> results = details.values().stream().collect(Collectors.groupingBy(
                network.foodsharing.poll.domain.PollOptionValue::getOptionIndex,
                Collectors.mapping(v -> new PollOptionValue().value(v.getValue()).votes(v.getVotes()),
                        Collectors.toList())));
        boolean showResults = PollRules.maySeeResults(item.status());
        Poll result = new Poll().id(poll.getId()).regionId(poll.getRegionId()).name(poll.getName())
                .type(PollType.fromValue(poll.getType().name())).scope(PollScope.fromValue(poll.getScope().name()))
                .startsAt(TimeMapper.toOffset(poll.getStartsAt())).endsAt(TimeMapper.toOffset(poll.getEndsAt()))
                .status(PollStatus.fromValue(item.status().name())).voted(item.voted())
                .eligibleCount(eligibleCount(item)).voteCount(poll.getVoteCount())
                .description(poll.getDescription()).author(summary(poll.getAuthorId()))
                .shuffleOptions(poll.isShuffleOptions()).cancelledAt(TimeMapper.toOffset(poll.getCancelledAt()))
                .cancelledBy(poll.getCancelledBy() == null ? null : summary(poll.getCancelledBy()))
                .mayVote(details.mayVote()).mayEdit(PollRules.mayEdit(actor, poll.getAuthorId(), item.status()))
                .mayCancel(PollRules.mayCancel(actor, poll.getAuthorId(), item.status()));
        result.setOptions(details.options().stream().map(o -> {
            PollOption option = new PollOption().index(o.getOptionIndex()).text(o.getText());
            if (showResults) {
                option.setValues(results.getOrDefault(o.getOptionIndex(), List.of()));
            }
            return option;
        }).toList());
        return result;
    }

    private static Integer eligibleCount(PollViews.PollItem item) {
        return PollRules.maySeeEligibleCount(item.actor(), item.poll().getAuthorId(), item.status())
                ? item.poll().getEligibleCount() : null;
    }

    private network.foodsharing.api.model.UserSummary summary(UUID userId) {
        return users.findAll(List.of(userId)).stream().findFirst().map(userMapper::toSummary).orElse(null);
    }
}
