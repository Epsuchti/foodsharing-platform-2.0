package network.foodsharing.profile.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.store.service.ProfileStoreInfo;
import network.foodsharing.user.service.CommitmentSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly commitments of a user (A6, upstream profile "Engagement"): the last {@code weeks} calendar weeks
 * (Monday, Europe/Zurich) including the current one, oldest first, from every {@link CommitmentSource}.
 */
@Service
public class CommitmentsService {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    public record Week(LocalDate weekStart, int pickups, int basketsOffered, int basketsShared, int eventsCreated,
                       int eventsParticipated) {
    }

    public record Commitments(List<Week> weeks, int responsibleStores) {
    }

    private final ProfilePermissionService access;
    private final ObjectProvider<CommitmentSource> sources;
    private final ProfileStoreInfo stores;
    private final java.time.Clock clock;

    public CommitmentsService(ProfilePermissionService access, ObjectProvider<CommitmentSource> sources,
                              ProfileStoreInfo stores, java.time.Clock clock) {
        this.access = access;
        this.sources = sources;
        this.stores = stores;
        this.clock = clock;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 USER_NOT_FOUND, 403 unless maySeeCommitments */
    @Transactional(readOnly = true)
    public Commitments get(UUID userId, int weeks) {
        ProfilePermissionService.Context context = access.context(userId);
        ProfilePermissionService.require(context.permissions().maySeeCommitments());
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZURICH);
        LocalDate first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(weeks - 1L);
        Instant from = first.atStartOfDay(ZURICH).toInstant();
        Instant to = first.plusWeeks(weeks).atStartOfDay(ZURICH).toInstant();
        List<CommitmentSource.Commitment> all = sources.orderedStream()
                .flatMap(source -> source.commitments(userId, from, to).stream()).toList();
        return new Commitments(bucket(first, weeks, all), stores.managedStoreCount(userId));
    }

    /** Pure bucketing: counts per kind and week starting at {@code first}. */
    static List<Week> bucket(LocalDate first, int weeks, List<CommitmentSource.Commitment> commitments) {
        int[][] counts = new int[weeks][CommitmentSource.Kind.values().length];
        for (CommitmentSource.Commitment c : commitments) {
            LocalDate day = c.at().atZone(ZURICH).toLocalDate();
            long index = java.time.temporal.ChronoUnit.WEEKS.between(first, day);
            if (index >= 0 && index < weeks && !day.isBefore(first)) {
                counts[(int) index][c.kind().ordinal()]++;
            }
        }
        List<Week> result = new ArrayList<>();
        for (int i = 0; i < weeks; i++) {
            int[] w = counts[i];
            result.add(new Week(first.plusWeeks(i), w[CommitmentSource.Kind.PICKUP.ordinal()],
                    w[CommitmentSource.Kind.BASKET_OFFERED.ordinal()], w[CommitmentSource.Kind.BASKET_SHARED.ordinal()],
                    w[CommitmentSource.Kind.EVENT_CREATED.ordinal()],
                    w[CommitmentSource.Kind.EVENT_PARTICIPATED.ordinal()]));
        }
        return result;
    }
}
