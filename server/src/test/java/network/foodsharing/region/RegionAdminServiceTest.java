package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.repository.HistoryEntryRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.UnitType;
import network.foodsharing.region.repository.RegionAdminRepository;
import network.foodsharing.region.repository.RegionClosureRepository;
import network.foodsharing.region.repository.RegionPostcodeRepository;
import network.foodsharing.region.repository.RegionRepository;
import network.foodsharing.region.service.RegionAdminService;
import network.foodsharing.region.service.RegionAdminService.FunctionInput;
import network.foodsharing.region.service.RegionAdminService.RegionInput;
import network.foodsharing.region.service.RegionsCsvImportService;
import network.foodsharing.region.service.Slugs;
import network.foodsharing.security.WorkingGroupFunction;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** Region administration (R7) and the regions CSV import: slugs, email names, functions, move, delete, import. */
@JpaSliceTest
@Import(RegionSliceConfig.class)
class RegionAdminServiceTest {

    @Autowired
    private RegionAdminService admin;

    @Autowired
    private RegionsCsvImportService importer;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RegionRepository regions;

    @Autowired
    private RegionAdminRepository admins;

    @Autowired
    private RegionPostcodeRepository postcodes;

    @Autowired
    private RegionClosureRepository closure;

    @Autowired
    private HistoryEntryRepository history;

    private RegionFixtures f;
    private Region country;
    private User ambassador;

    @BeforeEach
    void setUp() {
        f = RegionSliceConfig.fixtures(context);
        country = f.region("schweiz", UnitType.COUNTRY, null);
        ambassador = f.user("amb", Role.AMBASSADOR);
        TestAuth.login(TestUsers.principal(f.user("orga", Role.ORGA)));
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private RegionInput input(String name, String slug, UnitType type, Region parent, String emailName) {
        return new RegionInput(name, slug, type, parent == null ? null : parent.getId(), emailName, "teaser", null,
                null, "ch", null, false, null, null, "test");
    }

    @Test
    void onlyOrgaAdministratesRegions() {
        TestAuth.login(TestUsers.principal(ambassador));
        assertThatThrownBy(() -> admin.create(input("Bern", null, UnitType.CITY, country, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> admin.list(null, null, 0, 10)).isInstanceOf(ApiException.class);
    }

    @Test
    void createGeneratesUniqueSlugsAndChecksEmailNames() {
        var zuerich = admin.create(new RegionInput("Zürich Stadt", null, UnitType.CITY, country.getId(), "Zuerich",
                null, null, null, "ch", null, true, List.of(ambassador.getId()), null, "launch"));
        assertThat(zuerich.region().getSlug()).isEqualTo("zuerich-stadt");
        assertThat(zuerich.region().getEmailName()).isEqualTo("zuerich");
        assertThat(zuerich.region().getCountry()).isEqualTo("CH");
        assertThat(zuerich.region().getTimeZone()).isEqualTo("Europe/Zurich");
        assertThat(zuerich.ambassadors()).extracting(User::getId).containsExactly(ambassador.getId());
        assertThat(closure.ancestors(zuerich.region().getId())).containsExactly(country.getId(), zuerich.region().getId());
        assertThat(regions.findById(country.getId()).orElseThrow().isHasChildren()).isTrue();

        assertThat(admin.create(input("Zürich Stadt", null, UnitType.CITY, country, null)).region().getSlug())
                .isEqualTo("zuerich-stadt-2");
        assertThatThrownBy(() -> admin.create(input("X", "zuerich-stadt", UnitType.CITY, country, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.SLUG_TAKEN));
        assertThatThrownBy(() -> admin.create(input("Y", null, UnitType.CITY, country, "zuerich")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.EMAIL_NAME_TAKEN));
        assertThatThrownBy(() -> admin.create(input("Z", null, UnitType.CITY, country, "a b")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> admin.create(new RegionInput("Z", null, UnitType.CITY, country.getId(), null, null,
                null, "Nowhere/City", null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.INVALID_TIME_ZONE));
        assertThat(history.findAll()).filteredOn(entry -> entry.getAction().equals("CREATED")).hasSize(2);
    }

    @Test
    void functionsAreUniquePerRegionAndGroupsAreChecked() {
        Region city = f.region("city", UnitType.CITY, country);
        Region welcome = f.group("welcome", city, null);
        Region pr = f.group("pr", city, null);

        admin.setFunction(city.getId(), WorkingGroupFunction.WELCOME, welcome.getId(), null);
        assertThatThrownBy(() -> admin.setFunction(city.getId(), WorkingGroupFunction.PR, welcome.getId(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.DUPLICATE_FUNCTION_GROUP));
        assertThatThrownBy(() -> admin.setFunction(city.getId(), WorkingGroupFunction.PR, city.getId(), null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.GROUP_NOT_FOUND));
        admin.setFunction(city.getId(), WorkingGroupFunction.PR, pr.getId(), null);
        assertThat(admin.get(city.getId()).functions()).hasSize(2);

        assertThatThrownBy(() -> admin.update(city.getId(), new RegionInput(null, null, null, null, null, null, null,
                null, null, null, null, null, List.of(new FunctionInput(WorkingGroupFunction.PR, pr.getId()),
                        new FunctionInput(WorkingGroupFunction.PR, welcome.getId())), null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.DUPLICATE_FUNCTION_GROUP));
        admin.update(city.getId(), new RegionInput("City", null, null, null, null, null, null, null, null, null, null,
                null, List.of(new FunctionInput(WorkingGroupFunction.FSP, pr.getId())), null));
        assertThat(admin.get(city.getId()).functions()).singleElement()
                .satisfies(fg -> assertThat(fg.function()).isEqualTo(WorkingGroupFunction.FSP));
        admin.removeFunction(city.getId(), WorkingGroupFunction.FSP);
        assertThat(admin.get(city.getId()).functions()).isEmpty();
    }

    @Test
    void ambassadorsAreReplacedAndBecomeMembers() {
        Region city = f.region("city", UnitType.CITY, country);
        User other = f.user("other");
        admin.update(city.getId(), new RegionInput(null, null, null, null, null, null, null, null, null, null, null,
                List.of(ambassador.getId(), other.getId()), null, null));
        assertThat(admins.findUserIds(city.getId())).containsExactlyInAnyOrder(ambassador.getId(), other.getId());
        admin.update(city.getId(), new RegionInput(null, null, null, null, null, null, null, null, null, null, null,
                List.of(other.getId()), null, null));
        assertThat(admins.findUserIds(city.getId())).containsExactly(other.getId());
        assertThat(history.findAll()).extracting(entry -> entry.getAction())
                .contains("ADMIN_GRANTED", "ADMIN_REVOKED", "UPDATED");
    }

    @Test
    void deleteIsRefusedWithChildrenAndWithMembersUnlessForced() {
        Region canton = f.region("zh", UnitType.FEDERAL_STATE, country);
        Region city = f.region("city", UnitType.CITY, canton);
        f.member(ambassador, city);
        assertThatThrownBy(() -> admin.delete(canton.getId(), true, "x")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_HAS_CHILDREN));
        assertThatThrownBy(() -> admin.delete(city.getId(), false, "x")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.REGION_HAS_MEMBERS));
        admin.delete(city.getId(), true, "merged");
        regions.flush();
        assertThat(regions.findById(city.getId())).isEmpty();
        assertThat(regions.findById(canton.getId()).orElseThrow().isHasChildren()).isFalse();
        admin.move(canton.getId(), null, "root");
        assertThat(closure.ancestors(canton.getId())).containsExactly(canton.getId());
    }

    @Test
    void csvImportPreviewsAndAppliesRows() {
        Region existing = f.region("winterthur", UnitType.CITY, country);
        String csv = "parent_slug;slug;name;type;time_zone;country;postcodes\n"
                + "schweiz;kanton-thurgau;Kanton Thurgau;FEDERAL_STATE;;CH;\n"
                + "kanton-thurgau;;Frauenfeld;CITY;;CH;8500 8501\n"
                + "schweiz;winterthur;Winterthur;CITY;Europe/Zurich;CH;8400\n"
                + "nowhere;bad;Bad;CITY;;CH;\n"
                + "schweiz;;Mars;PLANET;Mars/Base;;\n"
                + "schweiz;frauenfeld;Dup;CITY;;CH;\n";

        var preview = importer.importCsv(csv, true);
        assertThat(preview.rows()).extracting(RegionsCsvImportService.Row::status).containsExactly(
                RegionsCsvImportService.Status.CREATE, RegionsCsvImportService.Status.CREATE,
                RegionsCsvImportService.Status.UPDATE, RegionsCsvImportService.Status.ERROR,
                RegionsCsvImportService.Status.ERROR, RegionsCsvImportService.Status.ERROR);
        assertThat(preview.rows().get(3).errors()).containsExactly("UNKNOWN_PARENT");
        assertThat(preview.rows().get(4).errors()).contains("INVALID_TYPE", "INVALID_TIME_ZONE");
        assertThat(preview.rows().get(5).errors()).containsExactly("DUPLICATE_SLUG");
        assertThat(preview.created()).isEqualTo(2);
        assertThat(regions.findBySlug("frauenfeld")).isEmpty();

        var result = importer.importCsv(csv, false);
        assertThat(result.dryRun()).isFalse();
        Region frauenfeld = regions.findBySlug("frauenfeld").orElseThrow();
        Region thurgau = regions.findBySlug("kanton-thurgau").orElseThrow();
        assertThat(frauenfeld.getParentId()).isEqualTo(thurgau.getId());
        assertThat(closure.ancestors(frauenfeld.getId())).containsExactly(country.getId(), thurgau.getId(),
                frauenfeld.getId());
        assertThat(postcodes.findByRegionId(frauenfeld.getId())).hasSize(2);
        assertThat(postcodes.findByRegionId(existing.getId())).singleElement()
                .satisfies(code -> assertThat(code.getPostcode()).isEqualTo("8400"));
        assertThat(history.findAll()).extracting(entry -> entry.getAction()).contains("REGIONS_IMPORTED");
        // repeating the import changes nothing
        assertThat(importer.importCsv(csv, true).rows()).extracting(RegionsCsvImportService.Row::status)
                .startsWith(RegionsCsvImportService.Status.UNCHANGED, RegionsCsvImportService.Status.UNCHANGED,
                        RegionsCsvImportService.Status.UNCHANGED);
    }

    @Test
    void slugs() {
        assertThat(Slugs.slugify("Zürcher Oberland")).isEqualTo("zuercher-oberland");
        assertThat(Slugs.slugify("Neuchâtel / Genève")).isEqualTo("neuchatel-geneve");
        assertThat(Slugs.slugify("  ")).isEqualTo("region");
    }
}
