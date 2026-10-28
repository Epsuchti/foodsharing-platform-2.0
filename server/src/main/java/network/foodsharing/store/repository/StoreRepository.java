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

    @Query("select s from Store s where s.id = :id and s.deletedAt is null")
    Optional<Store> findLive(@Param("id") UUID id);

    @Query("select s from Store s where s.regionId in :regionIds and s.deletedAt is null order by s.name")
    List<Store> findLiveInRegions(@Param("regionIds") Collection<UUID> regionIds);

    @Query("select s from Store s where s.id in :ids and s.deletedAt is null order by s.name")
    List<Store> findLiveIn(@Param("ids") Collection<UUID> ids);

    @Query("select s from Store s where s.chainId = :chainId and s.deletedAt is null order by s.name")
    List<Store> findLiveByChain(@Param("chainId") UUID chainId);

    @Query("select count(s) from Store s where s.regionId in :regionIds and s.deletedAt is null "
            + "and s.cooperationStatus = :status")
    long countByStatusInRegions(@Param("regionIds") Collection<UUID> regionIds,
                                @Param("status") CooperationStatus status);

    @Query("select count(s) from Store s where s.deletedAt is null and s.cooperationStatus = :status")
    long countByStatus(@Param("status") CooperationStatus status);

    @Query("select count(s) from Store s where s.chainId = :chainId and s.deletedAt is null")
    long countLiveByChain(@Param("chainId") UUID chainId);

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
