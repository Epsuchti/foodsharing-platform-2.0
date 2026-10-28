package network.foodsharing.intropickup.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntroPickupSlotRepository extends JpaRepository<IntroPickupSlot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IntroPickupSlot s where s.id = :id")
    Optional<IntroPickupSlot> findForUpdate(@Param("id") UUID id);

    @Query("select s from IntroPickupSlot s where s.introPickupId = :pickupId order by s.createdAt asc, s.id asc")
    List<IntroPickupSlot> findByPickup(@Param("pickupId") UUID pickupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IntroPickupSlot s where s.introPickupId = :pickupId order by s.createdAt asc, s.id asc")
    List<IntroPickupSlot> findByPickupForUpdate(@Param("pickupId") UUID pickupId);

    @Query("select s from IntroPickupSlot s where s.introPickupId in :pickupIds order by s.createdAt asc, s.id asc")
    List<IntroPickupSlot> findByPickups(@Param("pickupIds") Collection<UUID> pickupIds);

    /** Bookings (slots with a booking user) of a user in a region, any status, live EinAbs only. */
    @Query("select s from IntroPickupSlot s, IntroPickup p where p.id = s.introPickupId and p.deletedAt is null "
            + "and p.regionId = :regionId and s.bookingUserId = :userId")
    List<IntroPickupSlot> findBookingsOfUserInRegion(@Param("userId") UUID userId, @Param("regionId") UUID regionId);

    /** Bookings of users in a region, any status, live EinAbs only. */
    @Query("select s from IntroPickupSlot s, IntroPickup p where p.id = s.introPickupId and p.deletedAt is null "
            + "and p.regionId = :regionId and s.bookingUserId in :userIds")
    List<IntroPickupSlot> findBookingsOfUsersInRegion(@Param("userIds") Collection<UUID> userIds,
                                                      @Param("regionId") UUID regionId);

    /** All bookings of a user (all regions), live EinAbs only. */
    @Query("select s from IntroPickupSlot s, IntroPickup p where p.id = s.introPickupId and p.deletedAt is null "
            + "and s.bookingUserId = :userId")
    List<IntroPickupSlot> findBookingsOfUser(@Param("userId") UUID userId);

    /** Bookings (slots with a user) of a region, live EinAbs only. */
    @Query("select s from IntroPickupSlot s, IntroPickup p where p.id = s.introPickupId and p.deletedAt is null "
            + "and p.regionId = :regionId and s.bookingUserId is not null")
    List<IntroPickupSlot> findBookingsInRegion(@Param("regionId") UUID regionId);

    /** Booked / done / cancelled slots of the given users between two instants (import duplicate check). */
    @Query("select s from IntroPickupSlot s, IntroPickup p where p.id = s.introPickupId and p.deletedAt is null "
            + "and p.regionId = :regionId and s.bookingUserId in :userIds and s.status in :statuses "
            + "and p.startAt >= :from and p.startAt <= :to")
    List<IntroPickupSlot> findForImport(@Param("userIds") Collection<UUID> userIds, @Param("regionId") UUID regionId,
                                        @Param("statuses") Collection<SlotStatus> statuses,
                                        @Param("from") Instant from, @Param("to") Instant to);

    boolean existsByIntroPickupIdAndStatusIn(UUID introPickupId, Collection<SlotStatus> statuses);

    /** (region id, done at) of slots marked DONE in these regions within {@code from <= doneAt < to} (metrics). */
    @Query("select p.regionId, s.doneAt from IntroPickupSlot s, IntroPickup p where s.introPickupId = p.id "
            + "and s.status = network.foodsharing.intropickup.domain.SlotStatus.DONE and p.regionId in :regionIds "
            + "and s.doneAt >= :from and s.doneAt < :to")
    List<Object[]> doneIn(@Param("regionIds") java.util.Collection<UUID> regionIds,
                          @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);
}
