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
}
