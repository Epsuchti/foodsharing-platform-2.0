package network.foodsharing.poll.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.poll.domain.Poll;
import network.foodsharing.poll.domain.PollAction;
import network.foodsharing.poll.repository.PollRepository;
import network.foodsharing.poll.repository.PollVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Started polls → {@code POLL_STARTED} to the eligible voters; polls ending within 24 h that ran at least 30 h →
 * {@code POLL_ENDING_SOON} to eligible voters who have not voted yet (upstream voting notifications). Each poll is
 * announced once per kind ({@code start_notified_at}, {@code end_notified_at}).
 */
@Service
public class PollNotificationService {

    static final Duration ENDING_SOON = Duration.ofHours(24);
    static final Duration MIN_DURATION_FOR_REMINDER = Duration.ofHours(30);

    private final PollRepository polls;
    private final PollVoteRepository votes;
    private final PollEligibilityService eligibility;
    private final PollNotifier notifier;
    private final HistoryService history;
    private final Clock clock;

    public PollNotificationService(PollRepository polls, PollVoteRepository votes, PollEligibilityService eligibility,
                                   PollNotifier notifier, HistoryService history, Clock clock) {
        this.polls = polls;
        this.votes = votes;
        this.eligibility = eligibility;
        this.notifier = notifier;
        this.history = history;
        this.clock = clock;
    }

    /** @return how many announcements were sent */
    @Transactional
    public int notifyDue() {
        Instant now = clock.instant();
        int sent = 0;
        for (Poll poll : polls.startedNotAnnounced(now)) {
            Set<UUID> recipients = new LinkedHashSet<>(eligibility.eligibleUserIds(poll.getRegionId(), poll.getScope()));
            recipients.removeAll(votes.voterIds(poll.getId()));
            notifier.notify(poll, NotificationType.POLL_STARTED, recipients);
            poll.setStartNotifiedAt(now);
            polls.save(poll);
            history.record(history.event(PollAction.NOTIFIED, poll.getId()).region(poll.getRegionId())
                    .metadata("type", NotificationType.POLL_STARTED.name()).metadata("recipients", recipients.size())
                    .build());
            sent++;
        }
        for (Poll poll : polls.endingNotAnnounced(now, now.plus(ENDING_SOON))) {
            if (Duration.between(poll.getStartsAt(), poll.getEndsAt()).compareTo(MIN_DURATION_FOR_REMINDER) < 0) {
                continue;
            }
            Set<UUID> recipients = new LinkedHashSet<>(eligibility.eligibleUserIds(poll.getRegionId(), poll.getScope()));
            recipients.removeAll(votes.voterIds(poll.getId()));
            notifier.notify(poll, NotificationType.POLL_ENDING_SOON, recipients);
            poll.setEndNotifiedAt(now);
            polls.save(poll);
            history.record(history.event(PollAction.NOTIFIED, poll.getId()).region(poll.getRegionId())
                    .metadata("type", NotificationType.POLL_ENDING_SOON.name()).metadata("recipients", recipients.size())
                    .build());
            sent++;
        }
        return sent;
    }
}
