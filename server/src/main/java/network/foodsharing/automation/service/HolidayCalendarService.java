package network.foodsharing.automation.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.automation.repository.HolidayCalendarRepository;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.service.RegionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Public holidays per country and canton (A26). Provider {@code static} (local, e2e, test and default) reads
 * {@code holiday_calendar}; {@code nager} fetches {@code {nager-url}/api/v3/PublicHolidays/{year}/{country}} through
 * the HOLIDAY_CALENDAR breaker and caches per (country, year). National holidays have no canton ({@code global}).
 */
@Service
public class HolidayCalendarService {

    private static final Logger log = LoggerFactory.getLogger(HolidayCalendarService.class);

    /** Canton codes by the slug suffix of the seeded FEDERAL_STATE regions ({@code kanton-<suffix>}). */
    static final Map<String, String> CANTONS = Map.ofEntries(Map.entry("zuerich", "ZH"), Map.entry("bern", "BE"),
            Map.entry("luzern", "LU"), Map.entry("uri", "UR"), Map.entry("schwyz", "SZ"),
            Map.entry("obwalden", "OW"), Map.entry("nidwalden", "NW"), Map.entry("glarus", "GL"),
            Map.entry("zug", "ZG"), Map.entry("freiburg", "FR"), Map.entry("solothurn", "SO"),
            Map.entry("basel-stadt", "BS"), Map.entry("basel-landschaft", "BL"), Map.entry("schaffhausen", "SH"),
            Map.entry("appenzell-ausserrhoden", "AR"), Map.entry("appenzell-innerrhoden", "AI"),
            Map.entry("st-gallen", "SG"), Map.entry("graubuenden", "GR"), Map.entry("aargau", "AG"),
            Map.entry("thurgau", "TG"), Map.entry("tessin", "TI"), Map.entry("waadt", "VD"),
            Map.entry("wallis", "VS"), Map.entry("neuenburg", "NE"), Map.entry("genf", "GE"), Map.entry("jura", "JU"));

    /** One holiday; {@code canton == null} = national. */
    public record Holiday(LocalDate date, String name, String canton) {
    }

    /** Country and canton of a region (from its ancestors). */
    public record Place(String country, String canton) {
    }

    private final HolidayCalendarRepository table;
    private final RegionService regions;
    private final IntegrationRegistry integrations;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient http;
    private final Map<String, List<Holiday>> nagerCache = new ConcurrentHashMap<>();

    public HolidayCalendarService(HolidayCalendarRepository table, RegionService regions,
                                  IntegrationRegistry integrations, AppProperties properties, ObjectMapper objectMapper) {
        this.table = table;
        this.regions = regions;
        this.integrations = integrations;
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (nager()) {
            // The probe calls the API directly, never through the breaker (that recursed in the old Photon code).
            integrations.registerProbe(Integration.HOLIDAY_CALENDAR, this::probe);
        }
    }

    private boolean nager() {
        return "nager".equals(properties.getAutomation().getHolidayProvider());
    }

    /** Country (COUNTRY ancestor, default CH) and canton (FEDERAL_STATE ancestor) of the region. */
    @Transactional(readOnly = true)
    public Place placeOf(UUID regionId) {
        String country = null;
        String canton = null;
        for (Region region : regions.ancestors(regionId)) {
            if (region.getCountry() != null && !region.getCountry().isBlank()) {
                country = region.getCountry().strip().toUpperCase(java.util.Locale.ROOT);
            }
            if (region.getType() == UnitType.FEDERAL_STATE && region.getSlug() != null) {
                canton = CANTONS.get(region.getSlug().replaceFirst("^kanton-", ""));
            }
        }
        return new Place(country == null ? "CH" : country, canton);
    }

    /**
     * Name of the holiday on {@code date} at the place, if any.
     *
     * @throws PickupPolicyEvaluator.HolidayCalendarUnavailableException when the provider cannot answer
     */
    @Transactional(readOnly = true)
    public Optional<String> holidayName(LocalDate date, Place place) {
        return holidays(date.getYear(), place).stream().filter(h -> h.date().equals(date)).map(Holiday::name)
                .findFirst();
    }

    /** All holidays of the year at the place, national and cantonal, by date. */
    @Transactional(readOnly = true)
    public List<Holiday> holidays(int year, Place place) {
        if (!nager()) {
            return table.findInRange(place.country(), place.canton(), LocalDate.of(year, 1, 1),
                    LocalDate.of(year + 1, 1, 1)).stream()
                    .map(h -> new Holiday(h.getHolidayDate(), h.getName(), h.getCanton())).toList();
        }
        List<Holiday> all = nagerCache.computeIfAbsent(place.country() + ":" + year, key -> fetch(year,
                place.country()));
        return all.stream().filter(h -> h.canton() == null || h.canton().equals(place.canton())).toList();
    }

    private List<Holiday> fetch(int year, String country) {
        try {
            String body = integrations.call(Integration.HOLIDAY_CALENDAR, "holidays", () -> http.get()
                    .uri(url() + "/api/v3/PublicHolidays/" + year + "/" + country).retrieve().body(String.class));
            return parse(objectMapper.readTree(body == null ? "[]" : body), country);
        } catch (RuntimeException ex) {
            log.warn("Holiday calendar unavailable for {} {}: {}", country, year, ex.getMessage());
            throw new PickupPolicyEvaluator.HolidayCalendarUnavailableException("holiday calendar unavailable", ex);
        }
    }

    /** Nager v3 rows: {@code global} = national, else {@code counties: ["CH-ZH", …]}; v4 {@code nationalHoliday}. */
    static List<Holiday> parse(JsonNode rows, String country) {
        List<Holiday> result = new ArrayList<>();
        for (JsonNode row : rows) {
            LocalDate date = LocalDate.parse(row.path("date").asString());
            String name = row.path("localName").asString(row.path("name").asString("Feiertag"));
            boolean national = row.path("global").asBoolean(false) || row.path("nationalHoliday").asBoolean(false);
            if (national) {
                result.add(new Holiday(date, name, null));
                continue;
            }
            for (JsonNode county : row.path("counties")) {
                String code = county.asString("");
                if (code.startsWith(country + "-")) {
                    result.add(new Holiday(date, name, code.substring(country.length() + 1)));
                }
            }
        }
        result.sort(Comparator.comparing(Holiday::date));
        return List.copyOf(result);
    }

    private String url() {
        return properties.getAutomation().getNagerUrl().replaceFirst("/+$", "");
    }

    private boolean probe() {
        try {
            http.get().uri(url() + "/api/v3/AvailableCountries").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
