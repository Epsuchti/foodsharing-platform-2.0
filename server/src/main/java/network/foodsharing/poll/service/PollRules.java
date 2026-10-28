package network.foodsharing.poll.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.poll.domain.PollType;

/**
 * Poll rules (A36, upstream {@code VotingPermissions} and {@code VotingTransactions::isValidVote}) as pure functions.
 * Facts are resolved by {@link PollPermissionService}.
 */
public final class PollRules {

    /** Polls start at least this long after creation (upstream MIN_POLL_EDIT_TIME). */
    public static final Duration LEAD_TIME = Duration.ofHours(1);
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 50;

    /** UPCOMING / RUNNING / ENDED / CANCELLED. */
    public enum Status {
        UPCOMING, RUNNING, ENDED, CANCELLED
    }

    /**
     * The acting user's facts for one poll region: {@code group} = the region is a working group, {@code regionAdmin}
     * = admin of it, {@code votingGroupAdmin} = admin of the region's VOTING function group.
     */
    public record Actor(UUID userId, boolean orga, boolean member, boolean verified, boolean group,
                        boolean regionAdmin, boolean votingGroupAdmin) {
    }

    private PollRules() {
    }

    public static Status status(Instant startsAt, Instant endsAt, Instant cancelledAt, Instant now) {
        if (cancelledAt != null) {
            return Status.CANCELLED;
        }
        if (now.isBefore(startsAt)) {
            return Status.UPCOMING;
        }
        return now.isBefore(endsAt) ? Status.RUNNING : Status.ENDED;
    }

    /** list / see = region member (ORGA too). */
    public static boolean maySee(Actor actor) {
        return actor.member() || actor.orga();
    }

    /** create = verified member and (group: group admin; region: admin of the region's VOTING group). */
    public static boolean mayCreate(Actor actor) {
        return actor.member() && actor.verified() && (actor.group() ? actor.regionAdmin() : actor.votingGroupAdmin());
    }

    /** edit = author and before the start (not cancelled). */
    public static boolean mayEdit(Actor actor, UUID authorId, Status status) {
        return actor.userId().equals(authorId) && status == Status.UPCOMING;
    }

    /** cancel = author or ORGA, before the end and not yet cancelled. */
    public static boolean mayCancel(Actor actor, UUID authorId, Status status) {
        return (actor.userId().equals(authorId) || actor.orga())
                && (status == Status.UPCOMING || status == Status.RUNNING);
    }

    /** vote = member, running, not voted, eligible for the scope. */
    public static boolean mayVote(Actor actor, Status status, boolean voted, boolean eligible) {
        return actor.member() && status == Status.RUNNING && !voted && eligible;
    }

    /** results (values per option) only once the poll ended (cancelled polls keep them hidden). */
    public static boolean maySeeResults(Status status) {
        return status == Status.ENDED;
    }

    /** The eligible count: the author always, everyone once the poll ended. */
    public static boolean maySeeEligibleCount(Actor actor, UUID authorId, Status status) {
        return actor.userId().equals(authorId) || status == Status.ENDED || actor.orga();
    }

    /** Working groups offer FOODSAVERS and VERIFIED_FOODSAVERS only (upstream scope counts). */
    public static List<PollScope> scopes(boolean group) {
        return group ? List.of(PollScope.FOODSAVERS, PollScope.VERIFIED_FOODSAVERS) : List.of(PollScope.values());
    }

    /** start ≥ now + 1 h and start < end. */
    public static boolean validDates(Instant startsAt, Instant endsAt, Instant now) {
        return startsAt != null && endsAt != null && !startsAt.isBefore(now.plus(LEAD_TIME)) && startsAt.isBefore(endsAt);
    }

    /** 2–50 non-blank options, unique ignoring case and surrounding blanks, at most 500 characters. */
    public static boolean validOptions(List<String> options) {
        if (options == null || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (String option : options) {
            if (option == null || option.isBlank() || option.strip().length() > 500
                    || !seen.add(option.strip().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /** The values a voter may give each option: [1], [1, 0, -1] or [3 … -3]. */
    public static List<Integer> values(PollType type) {
        return switch (type) {
            case SELECT_ONE, SELECT_MULTIPLE -> List.of(1);
            case THUMBS -> List.of(1, 0, -1);
            case SCORE -> List.of(3, 2, 1, 0, -1, -2, -3);
        };
    }

    /**
     * Upstream isValidVote: every index is an option and every value allowed; SELECT_ONE exactly one option,
     * SELECT_MULTIPLE at most all options, THUMBS / SCORE a value for every option.
     */
    public static boolean validVote(PollType type, int optionCount, Map<Integer, Integer> votes) {
        if (votes == null) {
            return false;
        }
        List<Integer> allowed = values(type);
        for (Map.Entry<Integer, Integer> vote : votes.entrySet()) {
            if (vote.getKey() == null || vote.getKey() < 0 || vote.getKey() >= optionCount
                    || !allowed.contains(vote.getValue())) {
                return false;
            }
        }
        return switch (type) {
            case SELECT_ONE -> votes.size() == 1;
            case SELECT_MULTIPLE -> votes.size() <= optionCount;
            case THUMBS, SCORE -> votes.size() == optionCount;
        };
    }
}
