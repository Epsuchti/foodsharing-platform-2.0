package network.foodsharing.fsp.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.fsp.domain.FspCleaningSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FspCleaningSignupRepository extends JpaRepository<FspCleaningSignup, UUID> {

    /** Per user: confirmed past shifts since {@code since}, and the latest confirmed past shift (any time). */
    interface TeamStatsRow {
        UUID getUserId();

        Long getRecentCount();

        Instant getLastShiftAt();
    }

    Optional<FspCleaningSignup> findByFspIdAndUserIdAndShiftAt(UUID fspId, UUID userId, Instant shiftAt);

    @Query("select s from FspCleaningSignup s where s.fspId in :fspIds and s.shiftAt >= :from and s.shiftAt < :to")
    List<FspCleaningSignup> findInRange(@Param("fspIds") Collection<UUID> fspIds, @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query("select s from FspCleaningSignup s where s.userId = :userId and s.fspId in :fspIds "
            + "and s.shiftAt >= :from and s.shiftAt < :to")
    List<FspCleaningSignup> findOfUserInFsps(@Param("userId") UUID userId, @Param("fspIds") Collection<UUID> fspIds,
                                             @Param("from") Instant from, @Param("to") Instant to);

    /** Latest confirmed shift of a user in these Fairteiler before {@code before} (cleaning rule). */
    Optional<FspCleaningSignup> findFirstByFspIdInAndUserIdAndConfirmedTrueAndShiftAtLessThanOrderByShiftAtDescIdDesc(
            Collection<UUID> fspIds, UUID userId, Instant before);

    boolean existsByFspIdAndConfirmedFalseAndShiftAtAfter(UUID fspId, Instant now);

    @Query("select s.userId as userId, "
            + "sum(case when s.shiftAt >= :since then 1 else 0 end) as recentCount, max(s.shiftAt) as lastShiftAt "
            + "from FspCleaningSignup s where s.fspId = :fspId and s.confirmed = true and s.shiftAt < :now "
            + "group by s.userId")
    List<TeamStatsRow> teamStats(@Param("fspId") UUID fspId, @Param("since") Instant since,
                                 @Param("now") Instant now);

    /** The user's sign-ups with {@code from <= shiftAt < to}, by time. */
    List<FspCleaningSignup> findByUserIdAndShiftAtGreaterThanEqualAndShiftAtLessThanOrderByShiftAtAscIdAsc(
            UUID userId, Instant from, Instant to);

    /** Confirmed shifts of a user that already took place. */
    long countByUserIdAndConfirmedTrueAndShiftAtLessThan(UUID userId, Instant now);

    /** Confirmed shifts with {@code from <= shiftAt < to} in Fairteiler of these regions. */
    @Query("select count(s) from FspCleaningSignup s, FoodSharePoint f where f.id = s.fspId "
            + "and f.regionId in :regionIds and s.confirmed = true and s.shiftAt >= :from and s.shiftAt < :to")
    long countConfirmedInRegions(@Param("regionIds") Collection<UUID> regionIds, @Param("from") Instant from,
                                 @Param("to") Instant to);

    /** Rows of {@code [userId, count]}: upcoming sign-ups per user. */
    @Query("select s.userId, count(s) from FspCleaningSignup s where s.fspId = :fspId and s.shiftAt >= :now "
            + "group by s.userId")
    List<Object[]> upcomingCounts(@Param("fspId") UUID fspId, @Param("now") Instant now);
}
