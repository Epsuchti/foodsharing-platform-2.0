package network.foodsharing.region.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.csv.CsvParser;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.domain.RegionPostcode;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionPostcodeRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV import of regions (plan §16 "admin CSV import of regions"; ORGA). Columns
 * {@code parent_slug,slug,name,type,time_zone,country,postcodes} (header row optional, postcodes separated by spaces;
 * empty slug = generated from the name, empty parent = root). A dry run only returns the preview; otherwise valid rows
 * are created / updated (rows with errors are skipped) and REGIONS_IMPORTED is recorded. Parents may be earlier rows of
 * the same file. Error keys: MISSING_NAME, INVALID_TYPE, INVALID_TIME_ZONE, UNKNOWN_PARENT, DUPLICATE_SLUG,
 * WORKING_GROUP_NOT_IMPORTABLE. Lines whose first cell starts with {@code #} are comments (e.g. "model pending
 * ambassador confirmation" in {@code docs/data/zuerich/regions.csv}).
 */
@Service
public class RegionsCsvImportService {

    public enum Status { CREATE, UPDATE, UNCHANGED, ERROR }

    public record Row(int line, String parentSlug, String slug, String name, UnitType type, String timeZone,
                      String country, List<String> postcodes, Status status, List<String> errors) {
    }

    public record Preview(List<Row> rows, int created, int updated, int errors, boolean dryRun) {
    }

    private final RegionRepository regions;
    private final RegionPostcodeRepository postcodes;
    private final RegionTreeService tree;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final AppProperties properties;

    public RegionsCsvImportService(RegionRepository regions, RegionPostcodeRepository postcodes,
                                   RegionTreeService tree, CurrentActorService currentActor,
                                   PermissionService permissions, HistoryService history, AppProperties properties) {
        this.regions = regions;
        this.postcodes = postcodes;
        this.tree = tree;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.properties = properties;
    }

    /** @throws ApiException 403 unless ORGA, 400 IMPORT_TOO_LARGE */
    @Transactional
    public Preview importCsv(String csv, boolean dryRun) {
        permissions.requireOrga(currentActor.requirePrincipal());
        List<Row> rows = analyse(csv);
        int created = (int) rows.stream().filter(row -> row.status() == Status.CREATE).count();
        int updated = (int) rows.stream().filter(row -> row.status() == Status.UPDATE).count();
        int errors = (int) rows.stream().filter(row -> row.status() == Status.ERROR).count();
        if (!dryRun) {
            Map<String, UUID> createdIds = new HashMap<>();
            for (Row row : rows) {
                if (row.status() == Status.CREATE || row.status() == Status.UPDATE) {
                    apply(row, createdIds);
                }
            }
            history.record(history.event(RegionAction.REGIONS_IMPORTED, HistoryService.SYSTEM_SUBJECT_ID)
                    .metadata(Map.of("created", created, "updated", updated, "errors", errors)).build());
        }
        return new Preview(rows, created, updated, errors, dryRun);
    }

    List<Row> analyse(String csv) {
        List<List<String>> lines = CsvParser.parse(csv);
        int first = 0;
        while (first < lines.size() && CsvParser.isComment(lines.get(first))) {
            first++;
        }
        if (first < lines.size() && lines.get(first).get(0).strip().toLowerCase(Locale.ROOT).startsWith("parent")) {
            first++;
        }
        long dataLines = lines.subList(first, lines.size()).stream().filter(line -> !CsvParser.isComment(line)).count();
        if (dataLines > properties.getIntropickup().getImportMaxRows()) {
            throw ApiException.badRequest(ApiErrorCode.IMPORT_TOO_LARGE)
                    .withArg("max", String.valueOf(properties.getIntropickup().getImportMaxRows()));
        }
        Set<String> fileSlugs = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (int i = first; i < lines.size(); i++) {
            List<String> cells = lines.get(i);
            if (CsvParser.isComment(cells)) {
                continue;
            }
            String parentSlug = cell(cells, 0);
            String name = cell(cells, 2);
            String slug = cell(cells, 1).isEmpty() ? Slugs.slugify(name) : Slugs.slugify(cell(cells, 1));
            String timeZone = cell(cells, 4).isEmpty() ? properties.getRegion().getDefaultTimeZone() : cell(cells, 4);
            String country = cell(cells, 5).isEmpty() ? null : cell(cells, 5).toUpperCase(Locale.ROOT);
            List<String> codes = cell(cells, 6).isEmpty() ? List.of()
                    : Arrays.stream(cell(cells, 6).split("[\\s,;]+")).filter(s -> !s.isBlank()).toList();
            List<String> errors = new ArrayList<>();
            UnitType type = null;
            try {
                type = UnitType.valueOf(cell(cells, 3).toUpperCase(Locale.ROOT));
                if (type == UnitType.WORKING_GROUP) {
                    errors.add("WORKING_GROUP_NOT_IMPORTABLE");
                }
            } catch (IllegalArgumentException e) {
                errors.add("INVALID_TYPE");
            }
            if (name.isEmpty()) {
                errors.add("MISSING_NAME");
            }
            if (!RegionService.isValidZone(timeZone)) {
                errors.add("INVALID_TIME_ZONE");
            }
            if (!parentSlug.isEmpty() && regions.findBySlug(parentSlug).isEmpty() && !fileSlugs.contains(parentSlug)) {
                errors.add("UNKNOWN_PARENT");
            }
            if (!fileSlugs.add(slug)) {
                errors.add("DUPLICATE_SLUG");
            }
            Status status;
            if (!errors.isEmpty()) {
                status = Status.ERROR;
            } else {
                Optional<Region> existing = regions.findBySlug(slug);
                status = existing.isEmpty() ? Status.CREATE
                        : unchanged(existing.get(), parentSlug, name, type, timeZone, country, codes) ? Status.UNCHANGED
                        : Status.UPDATE;
            }
            rows.add(new Row(i + 1, parentSlug, slug, name, type, timeZone, country, codes, status, errors));
        }
        return rows;
    }

    private boolean unchanged(Region region, String parentSlug, String name, UnitType type, String timeZone,
                              String country, List<String> codes) {
        UUID parentId = parentSlug.isEmpty() ? null : regions.findBySlug(parentSlug).map(Region::getId).orElse(null);
        Set<String> current = new HashSet<>(postcodes.findByRegionId(region.getId()).stream()
                .map(RegionPostcode::getPostcode).toList());
        return Objects.equals(parentId, region.getParentId()) && name.equals(region.getName())
                && type == region.getType() && timeZone.equals(region.getTimeZone())
                && Objects.equals(country, region.getCountry()) && current.equals(new HashSet<>(codes));
    }

    private void apply(Row row, Map<String, UUID> createdIds) {
        UUID parentId = row.parentSlug().isEmpty() ? null : createdIds.containsKey(row.parentSlug())
                ? createdIds.get(row.parentSlug())
                : regions.findBySlug(row.parentSlug()).map(Region::getId).orElse(null);
        Region region = regions.findBySlug(row.slug()).orElse(null);
        if (region == null) {
            region = new Region();
            region.setSlug(row.slug());
            region.setName(row.name());
            region.setType(row.type());
            region.setParentId(parentId);
            region.setTimeZone(row.timeZone());
            region.setCountry(row.country());
            region = regions.saveAndFlush(region);
            tree.attach(region);
            history.record(history.event(RegionAction.CREATED, region.getId()).region(region.getId())
                    .metadata(Map.of("import", true, "slug", row.slug())).build());
            createdIds.put(row.slug(), region.getId());
        } else {
            region.setName(row.name());
            region.setType(row.type());
            region.setTimeZone(row.timeZone());
            region.setCountry(row.country());
            if (!Objects.equals(parentId, region.getParentId())) {
                tree.move(region.getId(), parentId, "CSV import");
            }
            history.record(history.event(RegionAction.UPDATED, region.getId()).region(region.getId())
                    .metadata(Map.of("import", true)).build());
        }
        postcodes.deleteByRegionId(region.getId());
        postcodes.flush();
        for (String code : row.postcodes()) {
            RegionPostcode postcode = new RegionPostcode();
            postcode.setRegionId(region.getId());
            postcode.setCountry(row.country() == null ? "CH" : row.country());
            postcode.setPostcode(code);
            postcodes.save(postcode);
        }
    }

    private static String cell(List<String> cells, int index) {
        return index < cells.size() && cells.get(index) != null ? cells.get(index).strip() : "";
    }
}
