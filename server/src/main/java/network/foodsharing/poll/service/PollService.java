package network.foodsharing.poll.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.poll.domain.Poll;
import network.foodsharing.poll.domain.PollAction;
import network.foodsharing.poll.domain.PollOption;
import network.foodsharing.poll.domain.PollOptionValue;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.poll.repository.PollOptionRepository;
import network.foodsharing.poll.repository.PollOptionValueRepository;
import network.foodsharing.poll.repository.PollRepository;
import network.foodsharing.poll.repository.PollVoteRepository;
import network.foodsharing.poll.service.PollPermissionService.PollContext;
import network.foodsharing.poll.service.PollViews.PollChange;
import network.foodsharing.poll.service.PollViews.PollDetails;
import network.foodsharing.poll.service.PollViews.PollDraft;
import network.foodsharing.poll.service.PollViews.PollItem;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Polls (V1): listing, create, update before start, cancel (A36). Votes: {@link VoteService}. */
@Service
public class PollService {

    private final PollRepository polls;
    private final PollOptionRepository options;
    private final PollOptionValueRepository values;
    private final PollVoteRepository votes;
    private final PollPermissionService access;
    private final PollEligibilityService eligibility;
    private final PollNotifier notifier;
    private final RegionService regions;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final Clock clock;

    public PollService(PollRepository polls, PollOptionRepository options, PollOptionValueRepository values,
                       PollVoteRepository votes, PollPermissionService access, PollEligibilityService eligibility,
                       PollNotifier notifier, RegionService regions, CurrentActorService currentActor,
                       HistoryService history, Clock clock) {
        this.polls = polls;
        this.options = options;
        this.values = values;
        this.votes = votes;
        this.access = access;
        this.eligibility = eligibility;
        this.notifier = notifier;
        this.regions = regions;
        this.currentActor = currentActor;
        this.history = history;
        this.clock = clock;
    }

    /** Polls of a region, newest start first. @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public List<PollItem> listRegion(UUID regionId) {
        PollRules.Actor actor = access.currentActor(regionId);
        PollPermissionService.require(PollRules.maySee(actor));
        List<Poll> list = polls.findByRegionIdOrderByStartsAtDescIdDesc(regionId);
        Set<UUID> voted = list.isEmpty() ? Set.of()
                : votes.votedPollIds(actor.userId(), list.stream().map(Poll::getId).toList());
        Instant now = clock.instant();
        return list.stream().map(p -> item(p, actor, voted.contains(p.getId()), now)).toList();
    }

    /** Running polls of my regions and groups that I may still vote in, ending soonest first. */
    @Transactional(readOnly = true)
    public List<PollItem> listMyOpen() {
        UUID me = currentActor.requireUserId();
        Set<UUID> regionIds = regions.activeRegionIdsOf(me);
        if (regionIds.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        List<Poll> running = polls.findByRegionIdInAndStartsAtLessThanEqualAndEndsAtGreaterThanOrderByEndsAtAscIdAsc(
                regionIds, now, now).stream().filter(p -> p.getCancelledAt() == null).toList();
        if (running.isEmpty()) {
            return List.of();
        }
        Set<UUID> voted = votes.votedPollIds(me, running.stream().map(Poll::getId).toList());
        Map<UUID, PollRules.Actor> actors = new HashMap<>();
        List<PollItem> result = new ArrayList<>();
        for (Poll poll : running) {
            if (voted.contains(poll.getId()) || !eligibility.eligibleUserIds(poll.getRegionId(), poll.getScope())
                    .contains(me)) {
                continue;
            }
            PollRules.Actor actor = actors.computeIfAbsent(poll.getRegionId(), access::currentActor);
            result.add(item(poll, actor, false, now));
        }
        return result;
    }

    /** @throws ApiException 404 POLL_NOT_FOUND, 403 ACCESS_DENIED */
    @Transactional(readOnly = true)
    public PollDetails get(UUID pollId) {
        return details(access.visiblePoll(pollId));
    }

    /** Eligible voters per scope offered for the region. @throws ApiException 404, 403 unless creator */
    @Transactional(readOnly = true)
    public Map<PollScope, Integer> scopeCounts(UUID regionId) {
        PollRules.Actor actor = access.currentActor(regionId);
        PollPermissionService.require(PollRules.mayCreate(actor) || actor.orga());
        Map<PollScope, Integer> counts = new LinkedHashMap<>();
        for (PollScope scope : PollRules.scopes(actor.group())) {
            counts.put(scope, eligibility.eligibleUserIds(regionId, scope).size());
        }
        return counts;
    }

    /**
     * Creates a poll with its options and the value rows per type; {@code eligible_count} is frozen. With
     * {@code notifyVoters} the eligible get {@code POLL_NEW} now and {@code POLL_STARTED} / {@code POLL_ENDING_SOON}
     * from {@link PollNotificationJob}; without it no poll bells are sent at all.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 ACCESS_DENIED, 400 POLL_DATES_INVALID / POLL_OPTIONS_INVALID /
     *                      VALIDATION_FAILED (scope not offered)
     */
    @Transactional
    public PollDetails create(PollDraft draft, boolean notifyVoters) {
        PollRules.Actor actor = access.currentActor(draft.regionId());
        PollPermissionService.require(PollRules.mayCreate(actor));
        Instant now = clock.instant();
        if (!PollRules.validDates(draft.startsAt(), draft.endsAt(), now)) {
            throw ApiException.badRequest(ApiErrorCode.POLL_DATES_INVALID);
        }
        if (!PollRules.validOptions(draft.options())) {
            throw ApiException.badRequest(ApiErrorCode.POLL_OPTIONS_INVALID);
        }
        if (!PollRules.scopes(actor.group()).contains(draft.scope()) || draft.type() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        Set<UUID> eligible = eligibility.eligibleUserIds(draft.regionId(), draft.scope());
        Poll poll = new Poll();
        poll.setRegionId(draft.regionId());
        poll.setAuthorId(actor.userId());
        poll.setName(draft.name().strip());
        poll.setDescription(blankToNull(draft.description()));
        poll.setType(draft.type());
        poll.setScope(draft.scope());
        poll.setStartsAt(draft.startsAt());
        poll.setEndsAt(draft.endsAt());
        poll.setShuffleOptions(draft.shuffleOptions());
        poll.setEligibleCount(eligible.size());
        if (!notifyVoters) {
            poll.setStartNotifiedAt(now);
            poll.setEndNotifiedAt(now);
        }
        poll = polls.save(poll);
        writeOptions(poll, draft.options());
        history.record(history.event(PollAction.CREATED, poll.getId()).region(poll.getRegionId())
                .metadata("type", poll.getType().name()).metadata("scope", poll.getScope().name())
                .metadata("eligible", eligible.size()).build());
        if (notifyVoters) {
            notifier.notify(poll, NotificationType.POLL_NEW, eligible);
        }
        return details(new PollContext(poll, actor));
    }

    /** Author before the start. @throws ApiException 404, 403 ACCESS_DENIED, 409 POLL_NOT_EDITABLE, 400 options */
    @Transactional
    public PollDetails update(UUID pollId, PollChange change) {
        PollContext context = access.visiblePoll(pollId);
        Poll poll = context.poll();
        PollPermissionService.require(context.actor().userId().equals(poll.getAuthorId()));
        if (!PollRules.mayEdit(context.actor(), poll.getAuthorId(), status(poll))) {
            throw ApiException.conflict(ApiErrorCode.POLL_NOT_EDITABLE);
        }
        if (change.options() != null && !PollRules.validOptions(change.options())) {
            throw ApiException.badRequest(ApiErrorCode.POLL_OPTIONS_INVALID);
        }
        var entry = history.event(PollAction.UPDATED, poll.getId()).region(poll.getRegionId());
        if (change.name() != null && !change.name().isBlank() && !change.name().strip().equals(poll.getName())) {
            entry.change("name", poll.getName(), change.name().strip());
            poll.setName(change.name().strip());
        }
        if (change.description() != null) {
            poll.setDescription(blankToNull(change.description()));
        }
        if (change.shuffleOptions() != null) {
            poll.setShuffleOptions(change.shuffleOptions());
        }
        polls.save(poll);
        if (change.options() != null) {
            values.deleteByPoll(poll.getId());
            options.deleteByPoll(poll.getId());
            options.flush();
            writeOptions(poll, change.options());
            entry.metadata("options", change.options().size());
        }
        history.record(entry.build());
        return details(context);
    }

    /**
     * Cancels a poll ({@code ends_at := now}); open poll bells go away and the notified voters get
     * {@code POLL_CANCELLED}.
     *
     * @throws ApiException 404, 403 ACCESS_DENIED, 409 POLL_NOT_CANCELLABLE
     */
    @Transactional
    public void cancel(UUID pollId) {
        PollContext context = access.visiblePoll(pollId);
        Poll poll = context.poll();
        PollPermissionService.require(context.actor().userId().equals(poll.getAuthorId()) || context.actor().orga());
        if (!PollRules.mayCancel(context.actor(), poll.getAuthorId(), status(poll))) {
            throw ApiException.conflict(ApiErrorCode.POLL_NOT_CANCELLABLE);
        }
        boolean notified = notifier.votersNotified(poll);
        Instant now = clock.instant();
        poll.setCancelledAt(now);
        poll.setCancelledBy(context.actor().userId());
        poll.setEndsAt(now);
        polls.save(poll);
        history.record(history.event(PollAction.CANCELLED, poll.getId()).region(poll.getRegionId()).build());
        notifier.removeOpenBells(poll.getId());
        if (notified) {
            Set<UUID> recipients = new LinkedHashSet<>(eligibility.eligibleUserIds(poll.getRegionId(), poll.getScope()));
            recipients.remove(context.actor().userId());
            notifier.notify(poll, NotificationType.POLL_CANCELLED, recipients);
        }
    }

    PollRules.Status status(Poll poll) {
        return PollRules.status(poll.getStartsAt(), poll.getEndsAt(), poll.getCancelledAt(), clock.instant());
    }

    PollDetails details(PollContext context) {
        Poll poll = context.poll();
        Instant now = clock.instant();
        boolean voted = votes.existsByPollIdAndUserId(poll.getId(), context.actor().userId());
        PollItem item = item(poll, context.actor(), voted, now);
        boolean mayVote = item.status() == PollRules.Status.RUNNING && !voted && context.actor().member()
                && eligibility.eligibleUserIds(poll.getRegionId(), poll.getScope()).contains(context.actor().userId());
        List<PollOptionValue> results = PollRules.maySeeResults(item.status())
                ? values.findByPollIdOrderByOptionIndexAscValueDesc(poll.getId()) : List.of();
        return new PollDetails(item, options.findByPollIdOrderByOptionIndex(poll.getId()), results, mayVote);
    }

    private PollItem item(Poll poll, PollRules.Actor actor, boolean voted, Instant now) {
        return new PollItem(poll, PollRules.status(poll.getStartsAt(), poll.getEndsAt(), poll.getCancelledAt(), now),
                voted, actor);
    }

    private void writeOptions(Poll poll, List<String> texts) {
        for (int i = 0; i < texts.size(); i++) {
            PollOption option = new PollOption();
            option.setPollId(poll.getId());
            option.setOptionIndex(i);
            option.setText(texts.get(i).strip());
            options.save(option);
            for (int value : PollRules.values(poll.getType())) {
                PollOptionValue row = new PollOptionValue();
                row.setPollId(poll.getId());
                row.setOptionIndex(i);
                row.setValue(value);
                values.save(row);
            }
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
