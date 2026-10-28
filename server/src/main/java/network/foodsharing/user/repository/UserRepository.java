package network.foodsharing.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByIdIn(Collection<UUID> ids);

    List<User> findBySleepStatus(network.foodsharing.user.domain.SleepStatus status);

    List<User> findByFoodsaverNumberIn(Collection<Long> numbers);

    long countByVerifiedTrueAndActiveTrue();

    List<User> findByVerifiedFalseAndActiveTrue();

    List<User> findByHomeRegionIdInAndActiveTrue(Collection<UUID> regionIds);

    List<User> findByRoleAndActiveTrue(network.foodsharing.api.model.Role role);

    List<User> findByActiveTrueAndNewsletterOptInTrue();

    @Query(value = "select nextval('foodsaver_number_seq')", nativeQuery = true)
    long nextFoodsaverNumber();

    Optional<User> findByTelegramChatId(String telegramChatId);

    /**
     * Name prefix search over active users (pickers, A33). {@code sharedWith} non-null = only users sharing an active
     * region/group membership with that user; {@code regionId} non-null = only active members of that region.
     */
    @Query(value = "select u.* from user_account u where u.active = true"
            + " and (lower(u.display_name) like :prefix or lower(u.first_name) like :prefix or lower(u.last_name) like :prefix)"
            + " and (cast(:regionId as uuid) is null or exists (select 1 from region_membership r where r.user_id = u.id"
            + " and r.region_id = cast(:regionId as uuid) and r.active = true))"
            + " and (cast(:sharedWith as uuid) is null or exists (select 1 from region_membership a join region_membership b"
            + " on a.region_id = b.region_id where a.user_id = u.id and b.user_id = cast(:sharedWith as uuid)"
            + " and a.active = true and b.active = true))"
            + " order by u.display_name, u.id limit :limit", nativeQuery = true)
    List<User> searchByNamePrefix(@org.springframework.data.repository.query.Param("prefix") String prefix,
                                  @org.springframework.data.repository.query.Param("regionId") UUID regionId,
                                  @org.springframework.data.repository.query.Param("sharedWith") UUID sharedWith,
                                  @org.springframework.data.repository.query.Param("limit") int limit);
}
