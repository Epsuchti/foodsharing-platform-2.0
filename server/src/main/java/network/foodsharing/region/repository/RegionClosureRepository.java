package network.foodsharing.region.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The closure table {@code region_closure(region_id, ancestor_id, depth)}: one row per region and each of its
 * ancestors, including the region itself at depth 0. Plain SQL ({@code INSERT … SELECT}, no recursive CTE) that
 * runs on H2 (PostgreSQL mode) and PostgreSQL.
 */
@Repository
public class RegionClosureRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RegionClosureRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds the rows of a new leaf: itself (depth 0) and the parent's ancestors (depth + 1). */
    public void insertLeaf(UUID regionId, UUID parentId) {
        jdbc.update("insert into region_closure (region_id, ancestor_id, depth) values (:id, :id, 0)",
                Map.of("id", regionId));
        if (parentId != null) {
            jdbc.update("insert into region_closure (region_id, ancestor_id, depth) "
                    + "select :id, ancestor_id, depth + 1 from region_closure where region_id = :parent",
                    Map.of("id", regionId, "parent", parentId));
        }
    }

    /** The region and all its descendants. */
    public List<UUID> subtree(UUID regionId) {
        return jdbc.queryForList("select region_id from region_closure where ancestor_id = :id",
                Map.of("id", regionId), UUID.class);
    }

    /** Ancestor ids incl. the region itself, root first. */
    public List<UUID> ancestors(UUID regionId) {
        return jdbc.queryForList("select ancestor_id from region_closure where region_id = :id order by depth desc",
                Map.of("id", regionId), UUID.class);
    }

    /** Ancestor ids incl. the regions themselves of several regions. */
    public List<UUID> ancestorsOfAll(Collection<UUID> regionIds) {
        if (regionIds.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("select distinct ancestor_id from region_closure where region_id in (:ids)",
                Map.of("ids", regionIds), UUID.class);
    }

    public boolean isAncestorOrSelf(UUID ancestorId, UUID regionId) {
        Integer count = jdbc.queryForObject("select count(*) from region_closure where region_id = :region "
                + "and ancestor_id = :ancestor", Map.of("region", regionId, "ancestor", ancestorId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * Re-attaches the subtree of {@code regionId} below {@code newParentId}: removes the links from the subtree to its
     * old ancestors and adds links to the new parent's ancestors. The caller has checked that the new parent is not
     * inside the subtree.
     */
    public void move(UUID regionId, UUID newParentId) {
        List<UUID> subtree = subtree(regionId);
        jdbc.update("delete from region_closure where region_id in (:subtree) and ancestor_id not in (:subtree)",
                Map.of("subtree", subtree));
        if (newParentId != null) {
            jdbc.update("insert into region_closure (region_id, ancestor_id, depth) "
                    + "select sub.region_id, sup.ancestor_id, sup.depth + sub.depth + 1 "
                    + "from region_closure sup, region_closure sub "
                    + "where sup.region_id = :parent and sub.ancestor_id = :id",
                    Map.of("parent", newParentId, "id", regionId));
        }
    }

    /** Number of closure rows (consistency checks). */
    public long count() {
        Long count = jdbc.queryForObject("select count(*) from region_closure", Map.of(), Long.class);
        return count == null ? 0 : count;
    }
}
