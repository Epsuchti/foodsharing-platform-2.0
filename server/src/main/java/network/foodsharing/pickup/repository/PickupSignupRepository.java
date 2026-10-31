package network.foodsharing.pickup.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupSignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PickupSignupRepository extends JpaRepository<PickupSignup, UUID> {

    /** Per user: number of past confirmed sign-ups, first and last pickup time. */
    interface MemberStatsRow {
        UUID getUserId();

        long getPickupCount();

        Instant getFirstPickupAt();

        Instant getLastPickupAt();
    }

    List<PickupSignup> findByStoreIdAndPickupAt(UUID storeId, Instant pickupAt);

    Optional<PickupSignup> findByStoreIdAndUserIdAndPickupAt(UUID storeId, UUID userId, Instant pickupAt);

    @Query("select s from PickupSignup s where s.storeId in :storeIds and s.pickupAt >= :from and s.pickupAt < :to")
    List<PickupSignup> findInRange(@Param("storeIds") Collection<UUID> storeIds, @Param("from") Instant from,
                                   @Param("to") Instant to);

    @Query("select s from PickupSignup s where s.userId = :userId and s.storeId in :storeIds and s.pickupAt >= :from "
            + "and s.pickupAt < :to")
    List<PickupSignup> findOfUserInStores(@Param("userId") UUID userId, @Param("storeIds") Collection<UUID> storeIds,
                                          @Param("from") Instant from, @Param("to") Instant to);

    @Query("select s from PickupSignup s where s.storeId = :storeId and s.userId = :userId and s.pickupAt > :now")
    List<PickupSignup> findFutureOfUser(@Param("storeId") UUID storeId, @Param("userId") UUID userId,
                                        @Param("now") Instant now);

    boolean existsByUserIdAndPickupAtAfter(UUID userId, Instant now);

    boolean existsByStoreId(UUID storeId);

    boolean existsByStoreIdAndConfirmedFalseAndPickupAtAfter(UUID storeId, Instant now);

    @Query("select s.userId as userId, count(s) as pickupCount, min(s.pickupAt) as firstPickupAt, "
            + "max(s.pickupAt) as lastPickupAt from PickupSignup s where s.storeId = :storeId and s.confirmed = true "
            + "and s.pickupAt < :now group by s.userId")
    List<MemberStatsRow> memberStats(@Param("storeId") UUID storeId, @Param("now") Instant now);

    List<PickupSignup> findByUserIdAndPickupAtGreaterThanEqualOrderByPickupAtAscIdAsc(UUID userId, Instant from);

    List<PickupSignup> findByUserIdAndPickupAtGreaterThanEqualAndPickupAtLessThanOrderByPickupAtAscIdAsc(UUID userId,
                                                                                               Instant from,
                                                                                               Instant to);

    org.springframework.data.domain.Page<PickupSignup> findByUserIdAndPickupAtLessThanOrderByPickupAtDescIdDesc(
            UUID userId, Instant before, org.springframework.data.domain.Pageable pageable);

    /** Latest confirmed sign-up of a user in a store before {@code before} (automation rules). */
    Optional<PickupSignup> findFirstByStoreIdAndUserIdAndConfirmedTrueAndPickupAtLessThanOrderByPickupAtDescIdDesc(
            UUID storeId, UUID userId, Instant before);

    /** Unconfirmed future sign-ups of the given stores (automation job). */
    List<PickupSignup> findByStoreIdInAndConfirmedFalseAndPickupAtAfterOrderByPickupAtAscIdAsc(Collection<UUID> storeIds,
                                                                                       Instant now);

    /** Past confirmed sign-ups of a user (member statistics). */
    long countByUserIdAndConfirmedTrueAndPickupAtLessThan(UUID userId, Instant now);

    Optional<PickupSignup> findFirstByUserIdAndConfirmedTrueAndPickupAtLessThanOrderByPickupAtDescIdDesc(UUID userId,
                                                                                                  Instant now);

    @Query("select s from PickupSignup s where s.confirmed = true and s.pickupAt >= :from and s.pickupAt < :to "
            + "and s.storeId in :storeIds")
    List<PickupSignup> findConfirmedInStores(@Param("storeIds") Collection<UUID> storeIds, @Param("from") Instant from,
                                             @Param("to") Instant to);

    @Query("select s from PickupSignup s where s.remindedAt is null and s.pickupAt > :from and s.pickupAt <= :to")
    List<PickupSignup> findToRemind(@Param("from") Instant from, @Param("to") Instant to);

    /** Rows of {@code [userId, max(pickupAt)]}: last confirmed past pickup per user (region member lists). */
    @Query("select s.userId, max(s.pickupAt) from PickupSignup s where s.userId in :userIds and s.confirmed = true "
            + "and s.pickupAt < :now group by s.userId")
    List<Object[]> lastConfirmedPickupAt(@Param("userIds") java.util.Collection<UUID> userIds,
                                         @Param("now") Instant now);
}
