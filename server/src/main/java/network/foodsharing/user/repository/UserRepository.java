package network.foodsharing.user.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserContacts;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Not deleted, not protected, no login (or registration) since {@code cutoff}, not warned since then (A13). */
    @Query("select u from User u where u.deletedAt is null and u.noAutomaticDelete = false "
            + "and coalesce(u.lastLoginAt, u.registeredAt) < :cutoff and (u.inactivityWarnedAt is null "
            + "or u.inactivityWarnedAt < coalesce(u.lastLoginAt, u.registeredAt))")
    List<User> findInactiveUnwarned(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff);

    Optional<User> findByEmailIgnoreCase(String email);

    /** Info mail recipients (A20): active, not deleted, info mails switched on. */
    java.util.List<User> findByActiveTrueAndInfoMailMessageTrueAndDeletedAtIsNull();

    List<User> findByIdIn(Collection<UUID> ids);

    List<User> findByActiveTrue();

    org.springframework.data.domain.Page<User> findByEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<User> findByHomeRegionIdAndEmailBouncedAtIsNotNullOrderByEmailBouncedAtDescIdDesc(
            UUID homeRegionId, org.springframework.data.domain.Pageable pageable);

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
     * Name prefix search over active users (pickers, A33), restricted to the contacts of {@code contactOf}
     * ({@link UserContacts}, decision 147); {@code regionId} non-null = only active members of that region.
     */
    @Query(value = "select u.* from user_account u where u.active = true"
            + " and (lower(u.display_name) like :prefix or lower(u.first_name) like :prefix or lower(u.last_name) like :prefix)"
            + " and (cast(:regionId as uuid) is null or exists (select 1 from region_membership r where r.user_id = u.id"
            + " and r.region_id = cast(:regionId as uuid) and r.active = true))"
            + " and " + UserContacts.CONDITION
            + " order by u.display_name, u.id limit :limit", nativeQuery = true)
    List<User> searchContactsByNamePrefix(@Param("prefix") String prefix, @Param("regionId") UUID regionId,
                                          @Param("contactOf") UUID contactOf, @Param("limit") int limit);

    /** Name prefix search over all active users (ORGA pickers); {@code regionId} as above. */
    @Query(value = "select u.* from user_account u where u.active = true"
            + " and (lower(u.display_name) like :prefix or lower(u.first_name) like :prefix or lower(u.last_name) like :prefix)"
            + " and (cast(:regionId as uuid) is null or exists (select 1 from region_membership r where r.user_id = u.id"
            + " and r.region_id = cast(:regionId as uuid) and r.active = true))"
            + " order by u.display_name, u.id limit :limit", nativeQuery = true)
    List<User> searchByNamePrefix(@Param("prefix") String prefix, @Param("regionId") UUID regionId,
                                  @Param("limit") int limit);

    /** Whether the user is an active member of at least one of the regions (email search scope, decision 147). */
    @Query(value = "select count(*) from region_membership r where r.user_id = :userId and r.active = true"
            + " and r.region_id in (:regionIds)", nativeQuery = true)
    long countActiveMemberships(@Param("userId") UUID userId, @Param("regionIds") Collection<UUID> regionIds);

    /**
     * Sets last_login_at without the optimistic lock: concurrent logins of one user (two devices, the load test)
     * must not fail with 409 on the user row's version (D17.1).
     */
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.lastLoginAt = :now where u.id = :id")
    int markLoggedIn(@Param("id") UUID id, @Param("now") Instant now);
}
