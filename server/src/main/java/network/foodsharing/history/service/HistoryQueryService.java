package network.foodsharing.history.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the history with access checks ({@link HistoryAccessPolicy}). */
@Service
@Transactional(readOnly = true)
public class HistoryQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final HistoryEntryRepository repository;
    private final HistoryAccessPolicy accessPolicy;
    private final CurrentActorService currentActorService;
    private final PermissionService permissionService;
    private final AppProperties properties;

    public HistoryQueryService(HistoryEntryRepository repository, HistoryAccessPolicy accessPolicy,
                               CurrentActorService currentActorService, PermissionService permissionService,
                               AppProperties properties) {
        this.repository = repository;
        this.accessPolicy = accessPolicy;
        this.currentActorService = currentActorService;
        this.permissionService = permissionService;
        this.properties = properties;
    }

    /**
     * Filtered list. With subject type + id the subject's access rule decides; any other query needs ORGA.
     *
     * @throws ApiException 403 HISTORY_SUBJECT_NOT_READABLE
     */
    public Page<HistoryEntry> list(HistoryFilter filter) {
        requireReadable(filter);
        return repository.findAll(specification(filter), PageRequest.of(filter.page(), filter.size(), NEWEST_FIRST));
    }

    /** Entries the current user performed or that concern them. */
    public Page<HistoryEntry> myActivity(HistoryFilter filter) {
        UUID userId = currentActorService.requireUserId();
        HistoryFilter own = new HistoryFilter(null, null, null, userId, filter.actions(), filter.from(), filter.to(),
                filter.page(), filter.size());
        return repository.findAll(specification(own), PageRequest.of(own.page(), own.size(), NEWEST_FIRST));
    }

    /** History of one subject, optionally limited to some action codes (e.g. the security log). */
    public Page<HistoryEntry> listForSubject(SubjectType subjectType, UUID subjectId, Collection<String> actions,
                                             int page, int size) {
        return list(HistoryFilter.forSubject(subjectType, subjectId, List.copyOf(actions), page, size));
    }

    /** CSV export rows (ORGA only, at most {@code app.history.export-max-rows}). */
    public List<HistoryEntry> export(HistoryFilter filter) {
        currentActorService.requireRole(network.foodsharing.api.model.Role.ORGA);
        int max = properties.getHistory().getExportMaxRows();
        return repository.findAll(specification(filter), PageRequest.of(0, max, NEWEST_FIRST)).getContent();
    }

    private void requireReadable(HistoryFilter filter) {
        SessionPrincipal actor = currentActorService.requirePrincipal();
        boolean subjectQuery = filter.subjectType() != null && filter.subjectId() != null;
        boolean allowed = subjectQuery
                ? accessPolicy.canRead(actor, filter.subjectType(), filter.subjectId())
                : permissionService.isOrga(actor);
        if (!allowed) {
            throw ApiException.forbidden(ApiErrorCode.HISTORY_SUBJECT_NOT_READABLE);
        }
    }

    static Specification<HistoryEntry> specification(HistoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.subjectType() != null) {
                predicates.add(cb.equal(root.get("subjectType"), filter.subjectType()));
            }
            if (filter.subjectId() != null) {
                predicates.add(cb.equal(root.get("subjectId"), filter.subjectId()));
            }
            if (filter.actorId() != null) {
                predicates.add(cb.equal(root.get("actorId"), filter.actorId()));
            }
            if (filter.involvingUserId() != null) {
                UUID userId = filter.involvingUserId();
                predicates.add(cb.or(
                        cb.equal(root.get("actorId"), userId),
                        cb.and(cb.equal(root.get("subjectType"), SubjectType.USER), cb.equal(root.get("subjectId"), userId)),
                        cb.equal(root.get("secondarySubjectId"), userId)));
            }
            if (!filter.actions().isEmpty()) {
                predicates.add(root.get("action").in(filter.actions()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
