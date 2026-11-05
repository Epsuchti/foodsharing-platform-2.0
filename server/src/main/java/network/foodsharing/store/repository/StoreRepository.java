package network.foodsharing.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.Store;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    /** Stores per region. */
    interface RegionCount {
        UUID getRegionId();

        long getCount();
    }

    @Query("select s from Store s where s.id = :id and s.deletedAt is null")
    Optional<Store> findLive(@Param("id") UUID id);

    @Query("select s from Store s where s.deletedAt is null")
    List<Store> findAllLive();

    /**
     * Live stores whose name, city or postcode contains the pattern ({@code %…%}, lower case, {@code \\} escapes),
     * without one store and without those of one cooperation status, by name (decision 167 S4 link picker).
     */
    @Query(value = "select s from Store s where s.deletedAt is null and s.id <> :excludeId "
            + "and s.cooperationStatus <> :excludeStatus and (lower(s.name) like :pattern escape '\\' "
            + "or lower(s.city) like :pattern escape '\\' or lower(s.postcode) like :pattern escape '\\') "
            + "order by lower(s.name), s.id",
            countQuery = "select count(s) from Store s where s.deletedAt is null and s.id <> :excludeId "
            + "and s.cooperationStatus <> :excludeStatus and (lower(s.name) like :pattern escape '\\' "
            + "or lower(s.city) like :pattern escape '\\' or lower(s.postcode) like :pattern escape '\\')")
    org.springframework.data.domain.Page<Store> searchLive(@Param("pattern") String pattern,
                                                          @Param("excludeId") UUID excludeId,
                                                          @Param("excludeStatus") CooperationStatus excludeStatus,
                                                          org.springframework.data.domain.Pageable pageable);

    @Query("select s from Store s where s.regionId in :regionIds and s.deletedAt is null order by s.name, s.id")
    List<Store> findLiveInRegions(@Param("regionIds") Collection<UUID> regionIds);

    @Query("select s from Store s where s.id in :ids and s.deletedAt is null order by s.name, s.id")
    List<Store> findLiveIn(@Param("ids") Collection<UUID> ids);

    @Query("select s from Store s where s.chainId = :chainId and s.deletedAt is null order by s.name, s.id")
    List<Store> findLiveByChain(@Param("chainId") UUID chainId);

    @Query("select count(s) from Store s where s.regionId in :regionIds and s.deletedAt is null "
            + "and s.cooperationStatus = :status")
    long countByStatusInRegions(@Param("regionIds") Collection<UUID> regionIds,
                                @Param("status") CooperationStatus status);

    @Query("select count(s) from Store s where s.deletedAt is null and s.cooperationStatus = :status")
    long countByStatus(@Param("status") CooperationStatus status);

    @Query("select count(s) from Store s where s.chainId = :chainId and s.deletedAt is null")
    long countLiveByChain(@Param("chainId") UUID chainId);

    @Query("select s.regionId as regionId, count(s) as count from Store s where s.deletedAt is null "
            + "and s.cooperationStatus = :status group by s.regionId")
    List<RegionCount> countLiveByRegion(@Param("status") CooperationStatus status);

    @Query("select s from Store s where s.publiclyNamed = true and s.deletedAt is null "
            + "and s.cooperationStatus = :status order by s.name, s.id")
    List<Store> findPartners(@Param("status") CooperationStatus status);

    long countByCategoryId(UUID categoryId);

    /** Serialises sign-ups per store (B4): a row lock on the store. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :id")
    Optional<Store> lockById(@Param("id") UUID id);

    @Query("select s.id from Store s where s.regionId = :regionId and s.useRegionPickupRule = true "
            + "and s.deletedAt is null")
    List<UUID> findPickupRuleStoreIds(@Param("regionId") UUID regionId);

    List<Store> findByCategoryId(UUID categoryId);
}
