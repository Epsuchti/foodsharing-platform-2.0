package network.foodsharing.region.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAdmin;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list (plan §14.4, AD1, ORGA): every ambassador / working-group admin ({@code region_admin}) with the units
 * they administer, filtered by name/email, unit kind and active flag, sorted by display name (then id). Enabling and
 * disabling an admin's account is the existing {@code PUT /api/admin/users/{userId}/active}.
 */
@Service
public class AdminListService {

    /** Which units count: regions (everything except working groups) or working groups. */
    public enum UnitKind { REGION, WORKING_GROUP }

    /** One admin with the units they administer (sorted by name). */
    public record AdminEntry(User user, List<Region> units) {
    }

    static final int MAX_SIZE = 100;

    private final RegionAdminRepository admins;
    private final RegionRepository regions;
    private final UserService users;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;

    public AdminListService(RegionAdminRepository admins, RegionRepository regions, UserService users,
                            PermissionService permissions, CurrentActorService currentActor) {
        this.admins = admins;
        this.regions = regions;
        this.users = users;
        this.permissions = permissions;
        this.currentActor = currentActor;
    }

    /** @throws ApiException 403 ACCESS_DENIED unless ORGA, 400 VALIDATION_FAILED for page/size/q out of range */
    @Transactional(readOnly = true)
    public Page<AdminEntry> list(String q, UnitKind kind, Boolean active, int page, int size) {
        permissions.requireOrga(currentActor.requirePrincipal());
        if (page < 0 || size < 1 || size > MAX_SIZE || (q != null && q.length() > 100)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", page < 0 ? "page"
                    : size < 1 || size > MAX_SIZE ? "size" : "q");
        }
        List<RegionAdmin> rows = admins.findAll();
        Map<UUID, Region> units = regions.findAllById(rows.stream().map(RegionAdmin::getRegionId).distinct().toList())
                .stream().filter(region -> matchesKind(region, kind))
                .collect(Collectors.toMap(Region::getId, Function.identity()));
        Map<UUID, List<Region>> byUser = new LinkedHashMap<>();
        for (RegionAdmin row : rows) {
            Region unit = units.get(row.getRegionId());
            if (unit != null) {
                byUser.computeIfAbsent(row.getUserId(), id -> new ArrayList<>()).add(unit);
            }
        }
        List<AdminEntry> entries = users.findAll(byUser.keySet()).stream()
                .filter(user -> matches(user, q, active))
                .map(user -> new AdminEntry(user, byUser.get(user.getId()).stream().distinct()
                        .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER)).toList()))
                .sorted(ORDER).toList();
        int from = (int) Math.min((long) page * size, entries.size());
        int to = Math.min(from + size, entries.size());
        return new PageImpl<>(entries.subList(from, to), PageRequest.of(page, size), entries.size());
    }

    static final Comparator<AdminEntry> ORDER = Comparator
            .comparing((AdminEntry entry) -> nullToEmpty(entry.user().getDisplayName()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.user().getId());

    static boolean matchesKind(Region region, UnitKind kind) {
        if (kind == null) {
            return true;
        }
        boolean group = region.getType() == UnitType.WORKING_GROUP;
        return kind == UnitKind.WORKING_GROUP ? group : !group;
    }

    /** Name/email filter (case-insensitive substring, blank = all) and active filter (null = all). */
    static boolean matches(User user, String q, Boolean active) {
        if (active != null && user.isActive() != active) {
            return false;
        }
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.strip().toLowerCase(Locale.ROOT);
        return nullToEmpty(user.getDisplayName()).toLowerCase(Locale.ROOT).contains(needle)
                || nullToEmpty(user.getEmail()).toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
