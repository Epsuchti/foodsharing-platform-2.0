package network.foodsharing.intropickup.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntroPickupRepository extends JpaRepository<IntroPickup, UUID> {

    @Query("select p from IntroPickup p where p.id = :id and p.deletedAt is null")
    Optional<IntroPickup> findLive(@Param("id") UUID id);

    @Query("select p from IntroPickup p where p.regionId = :regionId and p.deletedAt is null and p.startAt >= :from "
            + "order by p.startAt asc, p.id asc")
    List<IntroPickup> findUpcoming(@Param("regionId") UUID regionId, @Param("from") Instant from);

    @Query("select p from IntroPickup p where p.regionId = :regionId and p.deletedAt is null "
            + "and p.startAt >= :from and p.startAt < :to order by p.startAt asc, p.id asc")
    List<IntroPickup> findBetween(@Param("regionId") UUID regionId, @Param("from") Instant from,
                                  @Param("to") Instant to);

    @Query("select p from IntroPickup p where p.regionId = :regionId and p.deletedAt is null order by p.startAt asc, p.id asc")
    List<IntroPickup> findByRegion(@Param("regionId") UUID regionId);

    @Query("select p from IntroPickup p where p.guideId = :guideId and p.deletedAt is null order by p.startAt asc, p.id asc")
    List<IntroPickup> findByGuide(@Param("guideId") UUID guideId);

    /** Live EinAbs of the guide linked to the store pickup (EA16, ix_intro_pickup_linked). */
    @Query("select p from IntroPickup p where p.guideId = :guideId and p.storeId = :storeId "
            + "and p.linkedPickupAt = :pickupAt and p.deletedAt is null order by p.id asc")
    List<IntroPickup> findLinked(@Param("guideId") UUID guideId, @Param("storeId") UUID storeId,
                                 @Param("pickupAt") Instant pickupAt);

    /** Live EinAbs linked to any of these pickups of the store (sign-out warning, EA16). */
    @Query("select p from IntroPickup p where p.storeId = :storeId and p.linkedPickupAt in :pickupAts "
            + "and p.deletedAt is null order by p.id asc")
    List<IntroPickup> findLinkedAt(@Param("storeId") UUID storeId, @Param("pickupAts") Collection<Instant> pickupAts);

    @Query("select p from IntroPickup p where p.id in :ids")
    List<IntroPickup> findAllIn(@Param("ids") Collection<UUID> ids);

    @Query("select distinct p.businessName from IntroPickup p where p.regionId = :regionId "
            + "and p.businessName is not null")
    List<String> findBusinessNames(@Param("regionId") UUID regionId);
}
