package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
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
import network.foodsharing.store.domain.StoreTeamChangedEvent;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreMembershipRepository;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.store.service.StoresCsvImportService.Row;
import network.foodsharing.store.service.StoresCsvImportService.Status;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D16.2 stores CSV import rules: permission, size limit, row errors / warnings, dedupe, geocoding, managers. */
class StoresCsvImportServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    static final UUID REGION = UUID.fromString("00000010-0001-4000-8000-000000000040");
    static final String HEADER = "name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,"
            + "team_status,public_info,manager_foodsaver_numbers\n";

    StoreRepository stores = mock(StoreRepository.class);
    StoreMembershipRepository memberships = mock(StoreMembershipRepository.class);
    StoreCategoryRepository categories = mock(StoreCategoryRepository.class);
    StoreChainRepository chains = mock(StoreChainRepository.class);
    StorePermissionService rights = mock(StorePermissionService.class);
    RegionService regions = mock(RegionService.class);
    UserService users = mock(UserService.class);
    GeocodingClient geocoding = mock(GeocodingClient.class);
    CurrentActorService currentActor = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    List<Object> published = new ArrayList<>();
    AppProperties properties = new AppProperties();
    List<Store> saved = new ArrayList<>();
    List<StoreMembership> savedMemberships = new ArrayList<>();
    StoresCsvImportService service;
    Store existing;
    User manager;

    @BeforeEach
    void setUp() {
        service = new StoresCsvImportService(stores, memberships, categories, chains, rights, regions, users, geocoding,
                currentActor, history, published::add, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        Region region = new Region();
        region.setId(REGION);
        when(regions.require(REGION)).thenReturn(region);
        when(currentActor.requirePrincipal())
                .thenReturn(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR, "a@b.c"));
        when(rights.mayCoordinateRegionStores(any(), eq(REGION))).thenReturn(true);
        StoreCategory bakery = new StoreCategory();
        bakery.setId(UUID.randomUUID());
        bakery.setName("Bäckerei");
        when(categories.findAll()).thenReturn(List.of(bakery));
        StoreChain coop = new StoreChain();
        coop.setId(UUID.randomUUID());
        coop.setName("Coop");
        when(chains.findAll()).thenReturn(List.of(coop));
        existing = new Store();
        existing.setId(UUID.randomUUID());
        existing.setRegionId(REGION);
        existing.setName("Bäckerei Müller");
        existing.setPostcode("8004");
        existing.setCategoryId(bakery.getId());
        existing.setLatitude(47.0);
        existing.setLongitude(8.0);
        existing.setCooperationStatus(CooperationStatus.COOPERATION_ESTABLISHED);
        existing.setTeamStatus(TeamSearchStatus.OPEN);
        when(stores.findLiveInRegions(List.of(REGION))).thenReturn(List.of(existing));
        manager = new User();
        manager.setId(UUID.randomUUID());
        manager.setFoodsaverNumber(1234L);
        when(users.findByFoodsaverNumbers(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> numbers = inv.getArgument(0);
            return numbers.contains(1234L) ? List.of(manager) : List.of();
        });
        when(stores.save(any())).thenAnswer(inv -> {
            Store store = inv.getArgument(0);
            if (store.getId() == null) {
                store.setId(UUID.randomUUID());
            }
            saved.add(store);
            return store;
        });
        when(memberships.save(any())).thenAnswer(inv -> {
            savedMemberships.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    static ApiErrorCode codeOf(Runnable action) {
        return StoreServiceTest.codeOf(action);
    }

    @Test
    void needsStoreCoordinationOfTheRegion() {
        when(rights.mayCoordinateRegionStores(any(), eq(REGION))).thenReturn(false);
        assertThat(codeOf(() -> service.importCsv(REGION, HEADER, true))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void rejectsTooLargeFiles() {
        properties.getIntropickup().setImportMaxRows(2);
        String csv = HEADER + "# comment lines do not count\nA,,,,8001\nB,,,,8001\nC,,,,8001\n";
        assertThat(codeOf(() -> service.importCsv(REGION, csv, true))).isEqualTo(ApiErrorCode.IMPORT_TOO_LARGE);
    }

    @Test
    void previewReportsRowErrorsWarningsAndDedupe() {
        String csv = "# model\n" + HEADER
                + "Neu Laden,baeckerei,COOP,Weg 1,8001,Zürich,,,cooperation-established,open,,1234 999\n"
                + ",Bäckerei,,,8001,,,,,,,\n"
                + "X,Metzgerei,Kette,,,,,,,,,\n"
                + "Y,,,,8001,,91,8,MAYBE,SOMETIMES,,\n"
                + "BÄCKEREI  müller,,,,8004,,,,,,,\n"
                + "Bäckerei Müller,,,,8004,,,,IN_NEGOTIATION,,,\n"
                + "neu laden,,,,8001,,,,,,,\n";
        var preview = service.importCsv(REGION, csv, true);
        List<Row> rows = preview.rows();
        assertThat(rows).extracting(Row::status).containsExactly(Status.CREATE, Status.ERROR, Status.ERROR,
                Status.ERROR, Status.UNCHANGED, Status.DUPLICATE, Status.DUPLICATE);
        assertThat(rows.get(0).line()).isEqualTo(3);
        assertThat(rows.get(0).cooperationStatus()).isEqualTo(CooperationStatus.COOPERATION_ESTABLISHED);
        assertThat(rows.get(0).teamStatus()).isEqualTo(TeamSearchStatus.OPEN);
        assertThat(rows.get(0).warnings()).containsExactly("MISSING_COORDINATES", "UNKNOWN_USER");
        assertThat(rows.get(1).errors()).containsExactly("MISSING_NAME");
        assertThat(rows.get(2).errors()).containsExactly("MISSING_POSTCODE", "UNKNOWN_CATEGORY", "UNKNOWN_CHAIN");
        assertThat(rows.get(3).errors()).containsExactly("INVALID_COORDINATES", "INVALID_COOPERATION_STATUS",
                "INVALID_TEAM_STATUS");
        assertThat(rows.get(4).storeId()).isEqualTo(existing.getId());
        assertThat(rows.get(4).warnings()).isEmpty();
        assertThat(preview.created()).isEqualTo(1);
        assertThat(preview.skipped()).isEqualTo(3);
        assertThat(preview.errors()).isEqualTo(3);
        assertThat(saved).isEmpty();
        verify(history, never()).event(eq(StoreLogAction.STORES_IMPORTED), any());
    }

    @Test
    void importCreatesGeocodesAppointsManagersAndUpdatesMatches() {
        when(geocoding.geocodePostcode("CH", "8001")).thenReturn(Optional.of(new GeocodingClient.GeoPoint(47.37, 8.54)));
        when(geocoding.geocodePostcode("CH", "8099")).thenThrow(
                new GeocodingClient.GeocodingUnavailableException("down", null));
        String csv = HEADER
                + "Neu Laden,Bäckerei,Coop,Weg 1,8001,Zürich,,,,,Hintereingang,1234\n"
                + "Fern Laden,,,,8099,,,,,,,\n"
                + "Bäckerei Müller,,,,8004,,,,IN_NEGOTIATION,CLOSED,,1234\n";
        var result = service.importCsv(REGION, csv, false);
        assertThat(result.rows()).extracting(Row::status).containsExactly(Status.CREATE, Status.CREATE, Status.UPDATE);
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.storeId()).isNotNull());
        Store created = saved.get(0);
        assertThat(created.getRegionId()).isEqualTo(REGION);
        assertThat(created.getLatitude()).isEqualTo(47.37);
        assertThat(created.getCooperationStatus()).isEqualTo(CooperationStatus.UNCLEAR);
        assertThat(created.getPublicInfo()).isEqualTo("Hintereingang");
        assertThat(created.getStatusDate()).isEqualTo(NOW.atZone(ZoneOffset.UTC).toLocalDate());
        assertThat(result.rows().get(1).warnings()).containsExactly("MISSING_COORDINATES", "GEOCODING_FAILED");
        assertThat(saved.get(1).getLatitude()).isNull();
        assertThat(existing.getCooperationStatus()).isEqualTo(CooperationStatus.IN_NEGOTIATION);
        assertThat(existing.getTeamStatus()).isEqualTo(TeamSearchStatus.CLOSED);
        assertThat(savedMemberships).hasSize(2).allSatisfy(m -> {
            assertThat(m.isManager()).isTrue();
            assertThat(m.getUserId()).isEqualTo(manager.getId());
        });
        assertThat(published).hasSize(2).allMatch(StoreTeamChangedEvent.class::isInstance);
        verify(history).event(eq(StoreLogAction.STORES_IMPORTED), eq(REGION));
        verify(history).event(eq(StoreLogAction.STORE_CREATED), eq(created.getId()));
        verify(history).event(eq(StoreLogAction.COOPERATION_STATUS_CHANGED), eq(existing.getId()));
        verify(history).event(eq(StoreLogAction.TEAM_STATUS_CHANGED), eq(existing.getId()));
    }

    @Test
    void normalisedKeyIgnoresCaseAccentsUmlautsAndPunctuation() {
        assertThat(StoresCsvImportService.key("Bäckerei  Müller-Söhne", " 8004 "))
                .isEqualTo(StoresCsvImportService.key("baeckerei mueller soehne", "8004"));
        assertThat(StoresCsvImportService.normalise("Café Crème")).isEqualTo("cafe creme");
        assertThat(StoresCsvImportService.key("Coop", "8004")).isNotEqualTo(StoresCsvImportService.key("Coop", "8005"));
    }
}
