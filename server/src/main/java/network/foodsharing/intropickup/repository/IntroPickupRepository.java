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
            + "order by p.startAt asc")
    List<IntroPickup> findUpcoming(@Param("regionId") UUID regionId, @Param("from") Instant from);

    @Query("select p from IntroPickup p where p.regionId = :regionId and p.deletedAt is null "
            + "and p.startAt >= :from and p.startAt < :to order by p.startAt asc")
    List<IntroPickup> findBetween(@Param("regionId") UUID regionId, @Param("from") Instant from,
                                  @Param("to") Instant to);

    @Query("select p from IntroPickup p where p.regionId = :regionId and p.deletedAt is null order by p.startAt asc")
    List<IntroPickup> findByRegion(@Param("regionId") UUID regionId);

    @Query("select p from IntroPickup p where p.guideId = :guideId and p.deletedAt is null order by p.startAt asc")
    List<IntroPickup> findByGuide(@Param("guideId") UUID guideId);

    @Query("select p from IntroPickup p where p.id in :ids")
    List<IntroPickup> findAllIn(@Param("ids") Collection<UUID> ids);

    @Query("select distinct p.businessName from IntroPickup p where p.regionId = :regionId "
            + "and p.businessName is not null")
    List<String> findBusinessNames(@Param("regionId") UUID regionId);
}
