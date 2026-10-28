package network.foodsharing.store.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.csv.CsvParser;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreCategory;
import network.foodsharing.store.domain.StoreChain;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.StoreTeamChangedEvent;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV import of the stores of one region (D16.2, decision 79; store coordination of the region, decision 50). Columns
 * {@code name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,
 * manager_foodsaver_numbers}; header row optional, {@code #} lines are comments. Live stores of the region are matched
 * by normalised name + postcode: a match is updated with the non-empty cells (managers are only added), an identical
 * match is UNCHANGED, a second row with the same key is DUPLICATE. Category and chain are matched by name (case and
 * accents ignored). Missing coordinates are geocoded from the postcode on import. A dry run only returns the preview.
 */
@Service
public class StoresCsvImportService {

    public enum Status { CREATE, UPDATE, UNCHANGED, DUPLICATE, ERROR }

    public record Row(int line, String name, String category, String chain, String street, String postcode,
                      String city, Double latitude, Double longitude, CooperationStatus cooperationStatus,
                      TeamSearchStatus teamStatus, String publicInfo, List<Long> managerNumbers, UUID storeId,
                      Status status, List<String> errors, List<String> warnings) {

        Row with(UUID id, List<String> moreWarnings) {
            List<String> all = new ArrayList<>(warnings);
            all.addAll(moreWarnings);
            return new Row(line, name, category, chain, street, postcode, city, latitude, longitude, cooperationStatus,
                    teamStatus, publicInfo, managerNumbers, id, status, errors, List.copyOf(all));
        }
    }

    public record Preview(List<Row> rows, int created, int updated, int skipped, int errors, boolean dryRun) {
    }

    private record Analysed(Row row, UUID categoryId, UUID chainId, Store existing, List<User> managers) {
    }

    private final StoreRepository stores;
    private final StoreMembershipRepository memberships;
    private final StoreCategoryRepository categories;
    private final StoreChainRepository chains;
    private final StorePermissionService rights;
    private final RegionService regions;
    private final UserService users;
    private final GeocodingClient geocoding;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    public StoresCsvImportService(StoreRepository stores, StoreMembershipRepository memberships,
                                  StoreCategoryRepository categories, StoreChainRepository chains,
                                  StorePermissionService rights, RegionService regions, UserService users,
                                  GeocodingClient geocoding, CurrentActorService currentActor, HistoryService history,
                                  ApplicationEventPublisher events, AppProperties properties, Clock clock) {
        this.stores = stores;
        this.memberships = memberships;
        this.categories = categories;
        this.chains = chains;
        this.rights = rights;
        this.regions = regions;
        this.users = users;
        this.geocoding = geocoding;
        this.currentActor = currentActor;
        this.history = history;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 unless store coordination of the region, 400 IMPORT_TOO_LARGE */
    @Transactional
    public Preview importCsv(UUID regionId, String csv, boolean dryRun) {
        Region region = regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.mayCoordinateRegionStores(actor, region.getId()));
        List<Analysed> analysed = analyse(region.getId(), csv);
        List<Row> rows = new ArrayList<>();
        for (Analysed item : analysed) {
            Status status = item.row().status();
            if (dryRun || (status != Status.CREATE && status != Status.UPDATE)) {
                rows.add(item.row());
            } else {
                rows.add(apply(region.getId(), item));
            }
        }
        int created = count(rows, Status.CREATE);
        int updated = count(rows, Status.UPDATE);
        int skipped = count(rows, Status.UNCHANGED) + count(rows, Status.DUPLICATE);
        int errors = count(rows, Status.ERROR);
        if (!dryRun) {
            history.record(history.event(StoreLogAction.STORES_IMPORTED, region.getId()).region(region.getId())
                    .metadata(Map.of("created", created, "updated", updated, "skipped", skipped, "errors", errors))
                    .build());
        }
        return new Preview(rows, created, updated, skipped, errors, dryRun);
    }

    List<Analysed> analyse(UUID regionId, String csv) {
        List<List<String>> lines = CsvParser.parse(csv);
        int first = 0;
        while (first < lines.size() && CsvParser.isComment(lines.get(first))) {
            first++;
        }
        if (first < lines.size() && lines.get(first).get(0).strip().equalsIgnoreCase("name")) {
            first++;
        }
        long dataLines = lines.subList(first, lines.size()).stream().filter(line -> !CsvParser.isComment(line)).count();
        int max = properties.getIntropickup().getImportMaxRows();
        if (dataLines > max) {
            throw ApiException.badRequest(ApiErrorCode.IMPORT_TOO_LARGE).withArg("max", String.valueOf(max));
        }
        Map<String, StoreCategory> categoryByName = new HashMap<>();
        categories.findAll().forEach(c -> categoryByName.put(normalise(c.getName()), c));
        Map<String, StoreChain> chainByName = new HashMap<>();
        chains.findAll().forEach(c -> chainByName.put(normalise(c.getName()), c));
        Map<String, Store> existingByKey = new HashMap<>();
        for (Store store : stores.findLiveInRegions(List.of(regionId))) {
            existingByKey.putIfAbsent(key(store.getName(), store.getPostcode()), store);
        }
        List<Long> allNumbers = new ArrayList<>();
        List<List<String>> data = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();
        for (int i = first; i < lines.size(); i++) {
            if (!CsvParser.isComment(lines.get(i))) {
                data.add(lines.get(i));
                lineNumbers.add(i + 1);
                allNumbers.addAll(numbers(cell(lines.get(i), 11)));
            }
        }
        Map<Long, User> userByNumber = new HashMap<>();
        users.findByFoodsaverNumbers(new HashSet<>(allNumbers)).forEach(u -> userByNumber.put(u.getFoodsaverNumber(), u));

        Set<String> fileKeys = new HashSet<>();
        List<Analysed> result = new ArrayList<>();
        for (int n = 0; n < data.size(); n++) {
            List<String> cells = data.get(n);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            String name = cell(cells, 0);
            String categoryName = cell(cells, 1);
            String chainName = cell(cells, 2);
            String street = cell(cells, 3);
            String postcode = cell(cells, 4);
            String city = cell(cells, 5);
            String publicInfo = cell(cells, 10);
            if (name.isEmpty()) {
                errors.add("MISSING_NAME");
            } else if (name.length() > 200) {
                errors.add("NAME_TOO_LONG");
            }
            if (postcode.isEmpty()) {
                errors.add("MISSING_POSTCODE");
            }
            if (street.length() > 200 || postcode.length() > 20 || city.length() > 100 || publicInfo.length() > 4000) {
                errors.add("VALUE_TOO_LONG");
            }
            StoreCategory category = categoryName.isEmpty() ? null : categoryByName.get(normalise(categoryName));
            if (!categoryName.isEmpty() && category == null) {
                errors.add("UNKNOWN_CATEGORY");
            }
            StoreChain chain = chainName.isEmpty() ? null : chainByName.get(normalise(chainName));
            if (!chainName.isEmpty() && chain == null) {
                errors.add("UNKNOWN_CHAIN");
            }
            Double latitude = null;
            Double longitude = null;
            try {
                latitude = cell(cells, 6).isEmpty() ? null : Double.valueOf(cell(cells, 6).replace(',', '.'));
                longitude = cell(cells, 7).isEmpty() ? null : Double.valueOf(cell(cells, 7).replace(',', '.'));
                if ((latitude == null) != (longitude == null) || latitude != null
                        && (Math.abs(latitude) > 90 || Math.abs(longitude) > 180)) {
                    errors.add("INVALID_COORDINATES");
                }
            } catch (NumberFormatException e) {
                errors.add("INVALID_COORDINATES");
            }
            if (latitude == null && longitude == null && !errors.contains("INVALID_COORDINATES")) {
                warnings.add("MISSING_COORDINATES");
            }
            CooperationStatus cooperation = enumValue(CooperationStatus.class, cell(cells, 8));
            if (!cell(cells, 8).isEmpty() && cooperation == null) {
                errors.add("INVALID_COOPERATION_STATUS");
            }
            TeamSearchStatus team = enumValue(TeamSearchStatus.class, cell(cells, 9));
            if (!cell(cells, 9).isEmpty() && team == null) {
                errors.add("INVALID_TEAM_STATUS");
            }
            List<Long> managerNumbers = numbers(cell(cells, 11));
            if (cell(cells, 11).split("[\\s,;]+").length > managerNumbers.size() && !cell(cells, 11).isEmpty()) {
                warnings.add("UNKNOWN_USER");
            }
            List<User> managers = new ArrayList<>();
            for (Long number : managerNumbers) {
                User user = userByNumber.get(number);
                if (user == null) {
                    if (!warnings.contains("UNKNOWN_USER")) {
                        warnings.add("UNKNOWN_USER");
                    }
                } else if (managers.size() < properties.getStore().getMaxManagers()) {
                    managers.add(user);
                } else if (!warnings.contains("TOO_MANY_MANAGERS")) {
                    warnings.add("TOO_MANY_MANAGERS");
                }
            }
            Store existing = null;
            Status status;
            if (!errors.isEmpty()) {
                status = Status.ERROR;
            } else if (!fileKeys.add(key(name, postcode))) {
                status = Status.DUPLICATE;
            } else {
                existing = existingByKey.get(key(name, postcode));
                if (existing == null) {
                    status = Status.CREATE;
                } else {
                    if (existing.getLatitude() != null) {
                        warnings.remove("MISSING_COORDINATES");
                    }
                    status = unchanged(existing, category, chain, street, city, latitude, longitude, cooperation, team,
                            publicInfo, managers) ? Status.UNCHANGED : Status.UPDATE;
                }
            }
            Row row = new Row(lineNumbers.get(n), name, categoryName, chainName, street, postcode, city, latitude,
                    longitude, cooperation, team, publicInfo, managerNumbers,
                    existing == null ? null : existing.getId(), status, List.copyOf(errors), List.copyOf(warnings));
            result.add(new Analysed(row, category == null ? null : category.getId(),
                    chain == null ? null : chain.getId(), existing, managers));
        }
        return result;
    }

    private boolean unchanged(Store store, StoreCategory category, StoreChain chain, String street, String city,
                              Double latitude, Double longitude, CooperationStatus cooperation, TeamSearchStatus team,
                              String publicInfo, List<User> managers) {
        Set<UUID> currentManagers = new HashSet<>(memberships.findByStoreId(store.getId()).stream()
                .filter(StoreMembership::isManager).map(StoreMembership::getUserId).toList());
        return (category == null || category.getId().equals(store.getCategoryId()))
                && (chain == null || chain.getId().equals(store.getChainId()))
                && (street.isEmpty() || street.equals(store.getStreet()))
                && (city.isEmpty() || city.equals(store.getCity()))
                && (latitude == null || latitude.equals(store.getLatitude()) && longitude.equals(store.getLongitude()))
                && (cooperation == null || cooperation == store.getCooperationStatus())
                && (team == null || team == store.getTeamStatus())
                && (publicInfo.isEmpty() || publicInfo.equals(store.getPublicInfo()))
                && managers.stream().map(User::getId).allMatch(currentManagers::contains);
    }

    private Row apply(UUID regionId, Analysed item) {
        Row row = item.row();
        boolean create = item.existing() == null;
        Store store = create ? new Store() : item.existing();
        TeamSearchStatus oldTeam = store.getTeamStatus();
        CooperationStatus oldCooperation = store.getCooperationStatus();
        if (create) {
            store.setRegionId(regionId);
            store.setName(row.name());
            store.setPostcode(row.postcode());
            store.setCountry("CH");
            store.setPrefetchDays(properties.getStore().getDefaultPrefetchDays());
            store.setCooperationStatus(row.cooperationStatus() == null ? CooperationStatus.UNCLEAR
                    : row.cooperationStatus());
            store.setStatusDate(LocalDate.now(clock));
        } else if (row.cooperationStatus() != null && row.cooperationStatus() != oldCooperation) {
            store.setCooperationStatus(row.cooperationStatus());
            store.setStatusDate(LocalDate.now(clock));
        }
        if (item.categoryId() != null) {
            store.setCategoryId(item.categoryId());
        }
        if (item.chainId() != null) {
            store.setChainId(item.chainId());
        }
        if (!row.street().isEmpty()) {
            store.setStreet(row.street());
        }
        if (!row.city().isEmpty()) {
            store.setCity(row.city());
        }
        if (!row.publicInfo().isEmpty()) {
            store.setPublicInfo(row.publicInfo());
        }
        if (row.teamStatus() != null) {
            store.setTeamStatus(row.teamStatus());
        }
        List<String> warnings = new ArrayList<>();
        if (row.latitude() != null) {
            store.setLatitude(row.latitude());
            store.setLongitude(row.longitude());
        } else if (store.getLatitude() == null) {
            Optional<GeocodingClient.GeoPoint> point;
            try {
                point = geocoding.geocodePostcode(store.getCountry(), row.postcode());
            } catch (GeocodingClient.GeocodingUnavailableException e) {
                point = Optional.empty();
            }
            point.ifPresentOrElse(p -> {
                store.setLatitude(p.latitude());
                store.setLongitude(p.longitude());
            }, () -> warnings.add("GEOCODING_FAILED"));
        }
        stores.save(store);
        if (create) {
            history.record(history.event(StoreLogAction.STORE_CREATED, store.getId()).region(regionId)
                    .metadata(Map.of("name", store.getName(), "import", true)).build());
        } else {
            history.record(history.event(StoreLogAction.STORE_UPDATED, store.getId()).region(regionId)
                    .metadata("import", true).build());
            if (oldCooperation != store.getCooperationStatus()) {
                history.record(history.event(StoreLogAction.COOPERATION_STATUS_CHANGED, store.getId()).region(regionId)
                        .change("cooperationStatus", oldCooperation, store.getCooperationStatus()).build());
            }
            if (oldTeam != store.getTeamStatus()) {
                history.record(history.event(StoreLogAction.TEAM_STATUS_CHANGED, store.getId()).region(regionId)
                        .change("teamStatus", oldTeam, store.getTeamStatus()).build());
            }
        }
        addManagers(store, item.managers());
        return row.with(store.getId(), warnings);
    }

    private void addManagers(Store store, List<User> managers) {
        int max = properties.getStore().getMaxManagers();
        List<StoreMembership> team = memberships.findByStoreId(store.getId());
        long current = team.stream().filter(StoreMembership::isManager).count();
        for (User user : managers) {
            Optional<StoreMembership> membership = team.stream().filter(m -> m.getUserId().equals(user.getId()))
                    .findFirst();
            if (membership.isPresent() && membership.get().isManager() || current >= max) {
                continue;
            }
            StoreMembership manager = membership.orElseGet(StoreMembership::new);
            StoreMembershipStatus oldStatus = membership.map(StoreMembership::getStatus).orElse(null);
            manager.setStoreId(store.getId());
            manager.setUserId(user.getId());
            manager.setStatus(StoreMembershipStatus.MEMBER);
            manager.setManager(true);
            if (manager.getAddedAt() == null) {
                manager.setAddedAt(clock.instant());
            }
            memberships.save(manager);
            current++;
            history.record(history.event(StoreLogAction.APPOINT_STORE_MANAGER, store.getId())
                    .region(store.getRegionId()).secondarySubject(user.getId()).metadata("import", true).build());
            events.publishEvent(new StoreTeamChangedEvent(store.getId(), user.getId(), oldStatus,
                    StoreMembershipStatus.MEMBER, true));
        }
    }

    static String normalise(String value) {
        if (value == null) {
            return "";
        }
        String folded = value.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("ß", "ss");
        return Normalizer.normalize(folded, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ").strip();
    }

    static String key(String name, String postcode) {
        return normalise(name) + "|" + (postcode == null ? "" : postcode.strip());
    }

    private static List<Long> numbers(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[\\s,;]+")).filter(s -> s.matches("\\d{1,18}")).map(Long::valueOf)
                .distinct().toList();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int count(List<Row> rows, Status status) {
        return (int) rows.stream().filter(row -> row.status() == status).count();
    }

    private static String cell(List<String> cells, int index) {
        return index < cells.size() && cells.get(index) != null ? cells.get(index).strip() : "";
    }
}
