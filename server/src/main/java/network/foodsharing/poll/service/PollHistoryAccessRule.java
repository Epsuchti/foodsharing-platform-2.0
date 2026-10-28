package network.foodsharing.poll.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.poll.repository.PollRepository;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** POLL history (no vote values, A36): readable by the members of the poll's region. */
@Component
public class PollHistoryAccessRule implements HistoryAccessRule {

    private final PollRepository polls;
    private final PollPermissionService access;
    private final RegionService regions;

    public PollHistoryAccessRule(PollRepository polls, PollPermissionService access, RegionService regions) {
        this.polls = polls;
        this.access = access;
        this.regions = regions;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.POLL;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return polls.findById(subjectId)
                .flatMap(poll -> regions.find(poll.getRegionId()))
                .map(region -> PollRules.maySee(access.actor(actor, region))).orElse(false);
    }
}
