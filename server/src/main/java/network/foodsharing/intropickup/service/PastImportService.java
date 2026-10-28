package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.csv.CsvParser;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.IntroPickupStoreAlias;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.IntroPickupStoreAliasRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import of past EinAbs from the protocol spreadsheet (port of the reference {@code PastEinAbImportService}). The file
 * contains personal data and free-text feedback: it is never stored, and neither the file nor the feedback is logged.
 * Preview and confirm analyse the text from scratch, so confirming again only adds what is still missing. Users are
 * found by foodsaver number; unknown numbers need attention (no placeholder accounts). Imported EinAbs never notify.
 */
@Service
public class PastImportService {

    public enum Status { CREATE, SKIP_DUPLICATE, NEEDS_ATTENTION }

    public enum Reason {
        MISSING_ID, INVALID_ID, UNKNOWN_USER, INVALID_DATE, FUTURE_DATE, MISSING_STORE, UNKNOWN_CATEGORY,
        USER_OTHER_REGION, ALREADY_IMPORTED, DUPLICATE_IN_FILE, NOT_COUNTED, FAIRTEILER_UNKNOWN, NO_FEEDBACK,
        CATEGORY_MAPPED, STORE_NAME_CORRECTED
    }

    private static final Set<Reason> BLOCKING = Set.of(Reason.MISSING_ID, Reason.INVALID_ID, Reason.UNKNOWN_USER,
            Reason.INVALID_DATE, Reason.FUTURE_DATE, Reason.MISSING_STORE, Reason.UNKNOWN_CATEGORY,
            Reason.USER_OTHER_REGION);
    static final int JOB_THRESHOLD = 500;
    private static final int MAX_COMMENT_LENGTH = 4000;
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\s*\\.\\s*(\\d{1,2})\\s*\\.?\\s*(\\d{2}|\\d{4})");

    /** One analysed row; {@code feedback} stays in memory only. */
    public static final class Row {
        int line;
        Long foodsaverNumber;
        UUID userId;
        String userDisplayName;
        String rawDate = "";
        LocalDate date;
        String rawStore = "";
        String store = "";
        String rawCategory = "";
        IntroPickupCategory category;
        boolean visitFairteiler;
        String feedback = "";
        Instant start;
        boolean notCounted;
        Status status;
        final List<Reason> reasons = new ArrayList<>();

        public int line() {
            return line;
        }

        public Long foodsaverNumber() {
            return foodsaverNumber;
        }

        public UUID userId() {
            return userId;
        }

        public String userDisplayName() {
            return userDisplayName;
        }

        public String rawDate() {
            return rawDate;
        }

        public LocalDate date() {
            return date;
        }

        public String rawStore() {
            return rawStore;
        }

        public String store() {
            return store;
        }

        public String rawCategory() {
            return rawCategory;
        }

        public IntroPickupCategory category() {
            return category;
        }

        public boolean visitFairteiler() {
            return visitFairteiler;
        }

        public boolean hasComment() {
            return !feedback.isEmpty();
        }

        public Status status() {
            return status;
        }

        public List<Reason> reasons() {
            return reasons;
        }
    }

    public record Mapping(String from, String to, int count) {
    }

    public record Counts(int totalRows, int willCreate, int willSkip, int needsAttention, int knownUsers,
                         int unknownUsers) {
    }

    public record Preview(Counts counts, List<Row> rows, List<Mapping> storeMappings, List<Mapping> categoryMappings) {
    }

    public record Result(int created, int skipped, int needsAttention, UUID jobId) {
    }

    private final RegionService regions;
    private final RegionPermissionResolver resolver;
    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupStoreAliasRepository aliases;
    private final IntroPickupAdminService admin;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final ProgressTracker tracker;
    private final IntroPickupViews views;
    private final AppProperties properties;

    public PastImportService(RegionService regions, RegionPermissionResolver resolver, IntroPickupRepository pickups,
                             IntroPickupSlotRepository slots, IntroPickupStoreAliasRepository aliases,
                             IntroPickupAdminService admin, UserService users, CurrentActorService currentActor,
                             HistoryService history, ProgressTracker tracker, IntroPickupViews views,
                             AppProperties properties) {
        this.regions = regions;
        this.resolver = resolver;
        this.pickups = pickups;
        this.slots = slots;
        this.aliases = aliases;
        this.admin = admin;
        this.users = users;
        this.currentActor = currentActor;
        this.history = history;
        this.tracker = tracker;
        this.views = views;
        this.properties = properties;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403, 400 VALIDATION_FAILED / IMPORT_TOO_LARGE */
    @Transactional(readOnly = true)
    public Preview preview(UUID regionId, String csv) {
        admin.requireAdmin(regionId);
        List<Row> rows = analyse(regionId, csv);
        Set<Long> known = new HashSet<>();
        Set<Long> unknown = new HashSet<>();
        int create = 0;
        int skip = 0;
        int attention = 0;
        for (Row row : rows) {
            switch (row.status) {
                case CREATE -> create++;
                case SKIP_DUPLICATE -> skip++;
                case NEEDS_ATTENTION -> attention++;
            }
            if (row.foodsaverNumber != null) {
                (row.userId == null ? unknown : known).add(row.foodsaverNumber);
            }
        }
        return new Preview(new Counts(rows.size(), create, skip, attention, known.size(), unknown.size()), rows,
                mappings(rows, true), mappings(rows, false));
    }

    /**
     * Creates the CREATE rows: an EinAb with one DONE (or, for "x", CANCELLED) booking, the importing admin as guide,
     * the feedback as admin comment. Larger files report progress through a job.
     */
    @Transactional
    public Result confirm(UUID regionId, String csv) {
        admin.requireAdmin(regionId);
        UUID adminId = currentActor.requireUserId();
        List<Row> rows = analyse(regionId, csv);
        List<Row> toCreate = rows.stream().filter(r -> r.status == Status.CREATE).toList();
        UUID jobId = rows.size() > JOB_THRESHOLD ? tracker.start(adminId, toCreate.size()) : null;
        int done = 0;
        try {
            for (Row row : toCreate) {
                IntroPickupSlot slot = admin.createPastBooking(regionId, adminId, row.userId, row.category, row.start,
                        row.store, row.visitFairteiler, true, row.notCounted);
                if (!row.feedback.isEmpty()) {
                    admin.addComment(slot, adminId, truncate(row.feedback), true);
                }
                if (jobId != null && ++done % 50 == 0) {
                    tracker.progress(jobId, done);
                }
            }
        } catch (RuntimeException e) {
            // the transaction rolls back; the job must not stay RUNNING for the polling client
            if (jobId != null) {
                tracker.fail(jobId, "IMPORT_FAILED");
            }
            throw e;
        }
        if (jobId != null) {
            tracker.complete(jobId, null);
        }
        int skipped = (int) rows.stream().filter(r -> r.status == Status.SKIP_DUPLICATE).count();
        int attention = (int) rows.stream().filter(r -> r.status == Status.NEEDS_ATTENTION).count();
        history.record(history.event(IntroPickupAction.IMPORTED, regionId).region(regionId)
                .metadata("created", toCreate.size()).metadata("skipped", skipped)
                .metadata("needsAttention", attention).build());
        return new Result(toCreate.size(), skipped, attention, jobId);
    }

    // ------------------------------------------------------------------------------------------ analysis

    List<Row> analyse(UUID regionId, String csv) {
        ZoneId zone = regions.zoneOf(regionId);
        List<List<String>> records = CsvParser.parse(csv == null ? "" : csv);
        if (records.size() < 2) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (records.size() > properties.getIntropickup().getImportMaxRows() + 1) {
            throw ApiException.badRequest(ApiErrorCode.IMPORT_TOO_LARGE);
        }
        Columns columns = Columns.of(records.getFirst());
        Instant now = views.now();
        LocalDate today = now.atZone(zone).toLocalDate();
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> cells = records.get(index);
            if (cells.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(parseRow(index + 1, cells, columns, today, now, zone));
        }
        applyStoreNames(regionId, rows);

        Set<Long> numbers = new HashSet<>();
        rows.stream().map(r -> r.foodsaverNumber).filter(Objects::nonNull).forEach(numbers::add);
        Map<Long, User> byNumber = new HashMap<>();
        users.findByFoodsaverNumbers(numbers).forEach(u -> byNumber.put(u.getFoodsaverNumber(), u));

        // Key: user + day + store; existing bookings cover earlier imports and hand-entered past EinAbs (±1 day).
        Set<String> known = new HashSet<>();
        Instant from = rows.stream().map(r -> r.start).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
        Instant to = rows.stream().map(r -> r.start).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
        Set<UUID> userIds = new HashSet<>();
        byNumber.values().forEach(u -> userIds.add(u.getId()));
        if (!userIds.isEmpty() && from != null) {
            List<IntroPickupSlot> existing = slots.findForImport(userIds, regionId,
                    Set.of(SlotStatus.BOOKED, SlotStatus.DONE, SlotStatus.CANCELLED),
                    from.minusSeconds(86_400), to.plusSeconds(86_400));
            Map<UUID, IntroPickup> byId = new HashMap<>();
            if (!existing.isEmpty()) {
                pickups.findAllIn(existing.stream().map(IntroPickupSlot::getIntroPickupId).distinct().toList())
                        .forEach(p -> byId.put(p.getId(), p));
            }
            for (IntroPickupSlot slot : existing) {
                IntroPickup pickup = byId.get(slot.getIntroPickupId());
                if (pickup != null) {
                    known.add(duplicateKey(slot.getBookingUserId(), pickup.getStartAt().atZone(zone).toLocalDate(),
                            pickup.getBusinessName()));
                }
            }
        }
        Set<String> seenInFile = new HashSet<>();
        // Members of the region or of a region below it (Zürich: newcomers join a Kreis, EinAbs belong to the city).
        Set<UUID> regionMembers = byNumber.isEmpty() ? Set.of()
                : regions.activeMembersAmong(regions.subtreeIds(regionId), userIds);
        for (Row row : rows) {
            User user = row.foodsaverNumber == null ? null : byNumber.get(row.foodsaverNumber);
            if (row.foodsaverNumber != null && user == null) {
                row.reasons.add(Reason.UNKNOWN_USER);
            }
            if (user != null) {
                row.userId = user.getId();
                row.userDisplayName = user.getDisplayName();
                if (!regionMembers.contains(user.getId())) {
                    row.reasons.add(Reason.USER_OTHER_REGION);
                }
            }
            decideStatus(row, known, seenInFile);
        }
        return rows;
    }

    private static void decideStatus(Row row, Set<String> known, Set<String> seenInFile) {
        if (row.reasons.stream().anyMatch(BLOCKING::contains)) {
            row.status = Status.NEEDS_ATTENTION;
            return;
        }
        String key = duplicateKey(row.userId, row.date, row.store);
        if (known.contains(key)) {
            row.status = Status.SKIP_DUPLICATE;
            row.reasons.add(Reason.ALREADY_IMPORTED);
        } else if (!seenInFile.add(key)) {
            row.status = Status.SKIP_DUPLICATE;
            row.reasons.add(Reason.DUPLICATE_IN_FILE);
        } else {
            row.status = Status.CREATE;
        }
    }

    static String duplicateKey(UUID userId, LocalDate date, String store) {
        return userId + "|" + date + "|" + (store == null ? "" : store.toLowerCase(Locale.ROOT).trim());
    }

    private static Row parseRow(int line, List<String> cells, Columns columns, LocalDate today, Instant now,
                                ZoneId zone) {
        Row row = new Row();
        row.line = line;
        String id = columns.get(cells, Columns.ID).replaceAll("\\s", "");
        if (id.isEmpty()) {
            row.reasons.add(Reason.MISSING_ID);
        } else if (!id.matches("\\d{1,18}")) {
            row.reasons.add(Reason.INVALID_ID);
        } else {
            row.foodsaverNumber = Long.parseLong(id);
        }
        row.rawDate = columns.get(cells, Columns.DATE);
        LocalDate date = parseDate(row.rawDate);
        if (date == null) {
            row.reasons.add(Reason.INVALID_DATE);
        } else {
            row.date = date;
            Instant noon = ZonedDateTime.of(date, LocalTime.NOON, zone).toInstant();
            // Only past EinAbs: a later date is flagged, and "today at noon" must not lie in the future.
            row.start = noon.isAfter(now) ? now : noon;
            if (date.isAfter(today)) {
                row.reasons.add(Reason.FUTURE_DATE);
            }
        }
        // "x" in the No. column: kept, but does not count (cancelled, no-show or failed).
        if (columns.get(cells, Columns.NUMBER).equalsIgnoreCase("x")) {
            row.notCounted = true;
            row.reasons.add(Reason.NOT_COUNTED);
        }
        row.rawStore = columns.get(cells, Columns.STORE);
        row.store = row.rawStore;
        if (row.rawStore.isEmpty()) {
            row.reasons.add(Reason.MISSING_STORE);
        }
        row.rawCategory = columns.get(cells, Columns.CATEGORY);
        row.category = mapCategory(row.rawCategory, row);
        if (row.category == null) {
            row.reasons.add(Reason.UNKNOWN_CATEGORY);
        }
        String visit = columns.get(cells, Columns.FAIRTEILER).toLowerCase(Locale.ROOT);
        row.visitFairteiler = visit.equals("ja") || visit.equals("yes");
        if (!visit.isEmpty() && !visit.equals("ja") && !visit.equals("nein") && !visit.equals("yes")
                && !visit.equals("no")) {
            row.reasons.add(Reason.FAIRTEILER_UNKNOWN);
        }
        row.feedback = columns.get(cells, Columns.FEEDBACK);
        if (row.feedback.isEmpty()) {
            row.reasons.add(Reason.NO_FEEDBACK);
        }
        return row;
    }

    static LocalDate parseDate(String raw) {
        Matcher matcher = DATE.matcher(raw == null ? "" : raw.trim());
        if (!matcher.matches()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(3));
        if (year < 100) {
            year += 2000;
        }
        try {
            return LocalDate.of(year, Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    /** Category labels of the EinAb table; case, umlauts, spaces and punctuation are ignored ("Take-Away"). */
    static IntroPickupCategory mapCategory(String raw, Row row) {
        String key = storeKey(raw);
        return switch (key) {
            case "lebensmittelladen", "supermarkt", "laden" -> IntroPickupCategory.SUPERMARKET;
            case "restaurant" -> IntroPickupCategory.RESTAURANT;
            case "baeckerei" -> IntroPickupCategory.BAKERY;
            case "takeout", "takeaway" -> IntroPickupCategory.TAKEOUT;
            case "markt" -> IntroPickupCategory.MARKET;
            case "messe" -> IntroPickupCategory.MESSE;
            // Both categories exist; this combined label means a large pick-up at a fair.
            case "messegrossabholung", "grossabholung" -> IntroPickupCategory.GROSSABHOLUNG;
            case "ftbesuch", "ftreinigung", "fairteiler" -> {
                row.reasons.add(Reason.CATEGORY_MAPPED);
                yield IntroPickupCategory.FAIRTEILER_CLEANING;
            }
            case "online" -> IntroPickupCategory.ONLINE;
            default -> null;
        };
    }

    /**
     * Spellings that differ only in case, spaces or punctuation are one store. The name used is, in this order: an
     * alias of the region, a store name that already exists in the region, the most frequent spelling in the file
     * (capitalised wins ties).
     */
    private void applyStoreNames(UUID regionId, List<Row> rows) {
        Map<String, String> aliasNames = new HashMap<>();
        for (IntroPickupStoreAlias alias : aliases.findByRegionIdOrderByAliasKeyAsc(regionId)) {
            // keys stored before umlaut folding ("bäckerei") are folded here, so old and new aliases both match
            aliasNames.put(storeKey(alias.getAliasKey()), alias.getStoreName());
        }
        Map<String, String> existing = new HashMap<>();
        for (String name : pickups.findBusinessNames(regionId)) {
            existing.putIfAbsent(storeKey(name), name);
        }
        Map<String, Map<String, Integer>> spellings = new HashMap<>();
        for (Row row : rows) {
            if (!row.rawStore.isEmpty()) {
                spellings.computeIfAbsent(storeKey(row.rawStore), k -> new HashMap<>()).merge(row.rawStore, 1,
                        Integer::sum);
            }
        }
        for (Row row : rows) {
            if (row.rawStore.isEmpty()) {
                continue;
            }
            String key = storeKey(row.rawStore);
            String target = aliasNames.get(key);
            if (target == null) {
                target = existing.get(key);
            }
            if (target == null) {
                target = spellings.get(key).entrySet().stream()
                        .max(Map.Entry.<String, Integer>comparingByValue()
                                .thenComparing(e -> Character.isUpperCase(e.getKey().charAt(0))))
                        .map(Map.Entry::getKey).orElse(row.rawStore);
            }
            row.store = target;
            if (!target.equals(row.rawStore)) {
                row.reasons.add(Reason.STORE_NAME_CORRECTED);
            }
        }
    }

    /**
     * Lower-case letters and digits only (also the alias key); German umlauts are folded to ae/oe/ue/ss and other
     * accents dropped, so "Bäckerei Müller", "BAECKEREI MUELLER" and "Bäckerei Müller" (decomposed) are one store.
     * Idempotent: {@code storeKey(storeKey(x)) == storeKey(x)}.
     */
    public static String storeKey(String name) {
        String folded = java.text.Normalizer.normalize(name.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFC)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        folded = java.text.Normalizer.normalize(folded, java.text.Normalizer.Form.NFD);
        StringBuilder key = new StringBuilder();
        for (char c : folded.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                key.append(c);
            }
        }
        return key.toString();
    }

    private static List<Mapping> mappings(List<Row> rows, boolean stores) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String[]> pairs = new LinkedHashMap<>();
        for (Row row : rows) {
            String from = stores ? row.rawStore : row.rawCategory;
            String to = stores ? row.store : (row.category == null ? null : row.category.name());
            if (from.isEmpty() || to == null || (stores && from.equals(to))) {
                continue;
            }
            String key = from + "\u0000" + to;
            pairs.putIfAbsent(key, new String[] {from, to});
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
        }
        return pairs.entrySet().stream().map(e -> new Mapping(e.getValue()[0], e.getValue()[1],
                counts.get(e.getKey())[0])).toList();
    }

    private static String truncate(String text) {
        return text.length() > MAX_COMMENT_LENGTH ? text.substring(0, MAX_COMMENT_LENGTH) : text;
    }

    /** Columns by header prefix (headers may contain line breaks), falling back to the usual order. */
    private record Columns(int[] index) {
        static final int ID = 0;
        static final int DATE = 1;
        static final int NUMBER = 2;
        static final int STORE = 3;
        static final int CATEGORY = 4;
        static final int GUIDE = 5;
        static final int FAIRTEILER = 6;
        static final int FEEDBACK = 7;
        private static final String[] PREFIXES = {"id", "datum", "no", "betrieb", "kategorie", "einab", "ft",
            "feedback"};

        static Columns of(List<String> header) {
            int[] index = {0, 1, 2, 3, 4, 5, 6, 7};
            for (int field = 0; field < PREFIXES.length; field++) {
                for (int column = 0; column < header.size(); column++) {
                    String name = header.get(column).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
                    if (name.startsWith(PREFIXES[field])) {
                        index[field] = column;
                        break;
                    }
                }
            }
            return new Columns(index);
        }

        String get(List<String> cells, int field) {
            int column = index[field];
            return column < cells.size() ? cells.get(column).replace(' ', ' ').trim() : "";
        }
    }
}
