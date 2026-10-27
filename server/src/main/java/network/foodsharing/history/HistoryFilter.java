package network.foodsharing.history;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Query for history entries. {@code involvingUserId}: entries the user performed or whose USER subject / secondary
 * subject is the user ("my activity").
 */
public record HistoryFilter(
        SubjectType subjectType,
        UUID subjectId,
        UUID actorId,
        UUID involvingUserId,
        List<String> actions,
        Instant from,
        Instant to,
        int page,
        int size) {

    public HistoryFilter {
        actions = actions == null ? List.of() : List.copyOf(actions);
        page = Math.max(0, page);
        size = size <= 0 ? 20 : Math.min(size, 100);
    }

    public static HistoryFilter forSubject(SubjectType subjectType, UUID subjectId, List<String> actions, int page, int size) {
        return new HistoryFilter(subjectType, subjectId, null, null, actions, null, null, page, size);
    }

    public HistoryFilter withInvolvingUser(UUID userId) {
        return new HistoryFilter(subjectType, subjectId, actorId, userId, actions, from, to, page, size);
    }

    public HistoryFilter withPaging(int page, int size) {
        return new HistoryFilter(subjectType, subjectId, actorId, involvingUserId, actions, from, to, page, size);
    }
}
