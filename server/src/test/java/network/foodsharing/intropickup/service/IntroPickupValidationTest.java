package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.service.IntroPickupGuideService.PickupInput;
import org.junit.jupiter.api.Test;

/** Guide input validation (online vs. on site) and the pure parts of the past import. */
class IntroPickupValidationTest {

    private final IntroPickupGuideService guides = new IntroPickupGuideService(null, null, null, null, null, null,
            null, null, null, null, null, null, new AppProperties());

    private static PickupInput input(IntroPickupCategory category, Integer slots, String address, String area,
                                     String name, String link, Integer minimum) {
        return new PickupInput(category, Instant.parse("2026-11-10T17:00:00Z"), slots, minimum, name, null, address,
                area, link, null, null, false, false);
    }

    private void expect(PickupInput input, ApiErrorCode code) {
        assertThatThrownBy(() -> guides.validate(input)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void onlineNeedsAnHttpLinkOnly() {
        assertThatCode(() -> guides.validate(input(IntroPickupCategory.ONLINE, null, null, null, null,
                "https://meet.jit.si/x", null))).doesNotThrowAnyException();
        expect(input(IntroPickupCategory.ONLINE, 1, "a", "b", "c", null, null),
                ApiErrorCode.ONLINE_CALL_LINK_REQUIRED);
        expect(input(IntroPickupCategory.ONLINE, 1, null, null, null, "ftp://x", null),
                ApiErrorCode.ONLINE_CALL_LINK_REQUIRED);
        expect(input(IntroPickupCategory.ONLINE, 1, null, null, null, "https://" + "x".repeat(2000), null),
                ApiErrorCode.ONLINE_CALL_LINK_REQUIRED);
    }

    @Test
    void onSiteNeedsSlotsAddressAreaAndName() {
        assertThatCode(() -> guides.validate(input(IntroPickupCategory.BAKERY, 3, "Strasse 1", "Kreis 4", "Bäckerei",
                null, 0))).doesNotThrowAnyException();
        expect(input(IntroPickupCategory.BAKERY, 0, "a", "b", "c", null, null), ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
        expect(input(IntroPickupCategory.BAKERY, 4, "a", "b", "c", null, null), ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
        expect(input(IntroPickupCategory.BAKERY, 1, " ", "b", "c", null, null), ApiErrorCode.LOCATION_REQUIRED);
        expect(input(IntroPickupCategory.BAKERY, 1, "a", null, "c", null, null), ApiErrorCode.PUBLIC_LOCATION_REQUIRED);
        expect(input(IntroPickupCategory.BAKERY, 1, "a", "b", "", null, null), ApiErrorCode.BUSINESS_NAME_REQUIRED);
        expect(input(IntroPickupCategory.BAKERY, 1, "a", "b", "c", null, -1), ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void importDatesCategoriesAndStoreKeys() {
        assertThat(PastImportService.parseDate("3.4.25")).isEqualTo(LocalDate.of(2025, 4, 3));
        assertThat(PastImportService.parseDate(" 03 . 04 . 2025 ")).isEqualTo(LocalDate.of(2025, 4, 3));
        assertThat(PastImportService.parseDate("31.02.2025")).isNull();
        assertThat(PastImportService.parseDate("2025-04-03")).isNull();
        PastImportService.Row row = new PastImportService.Row();
        assertThat(PastImportService.mapCategory("Lebensmittelladen", row)).isEqualTo(IntroPickupCategory.SUPERMARKET);
        assertThat(PastImportService.mapCategory("Messe/Grossabholung", row))
                .isEqualTo(IntroPickupCategory.GROSSABHOLUNG);
        assertThat(PastImportService.mapCategory("Bäckerei", row)).isEqualTo(IntroPickupCategory.BAKERY);
        assertThat(row.reasons()).isEmpty();
        assertThat(PastImportService.mapCategory("FT-Besuch", row)).isEqualTo(IntroPickupCategory.FAIRTEILER_CLEANING);
        assertThat(row.reasons()).containsExactly(PastImportService.Reason.CATEGORY_MAPPED);
        assertThat(PastImportService.mapCategory("Zirkus", row)).isNull();
        assertThat(PastImportService.storeKey("Babu's  Bakery!")).isEqualTo("babusbakery");
        assertThat(PastImportService.storeKey("Tiny Fish Löwenstrasse")).isEqualTo("tinyfishloewenstrasse");
    }

    /** D16.3 (Zürich run): umlauts / accents / case fold, so the EinAb table's spellings meet aliases and stores. */
    @Test
    void importStoreKeysAndCategoriesFoldUmlautsCaseAndPunctuation() {
        assertThat(PastImportService.storeKey("BÄCKEREI MÜLLER")).isEqualTo(PastImportService.storeKey("Baeckerei Mueller"))
                .isEqualTo("baeckereimueller");
        // decomposed umlaut (macOS / Excel exports) and other accents
        assertThat(PastImportService.storeKey("Bu\u0308ro Cafe\u0301")).isEqualTo("buerocafe");
        assertThat(PastImportService.storeKey("Strassenküche Größe")).isEqualTo("strassenkuechegroesse");
        // idempotent: stored (old, unfolded) alias keys are folded on read
        assertThat(PastImportService.storeKey("bäckereimüller")).isEqualTo("baeckereimueller");
        assertThat(PastImportService.storeKey(PastImportService.storeKey("Löwen Bräu"))).isEqualTo("loewenbraeu");
        PastImportService.Row row = new PastImportService.Row();
        assertThat(PastImportService.mapCategory("Take-Away", row)).isEqualTo(IntroPickupCategory.TAKEOUT);
        assertThat(PastImportService.mapCategory("take away", row)).isEqualTo(IntroPickupCategory.TAKEOUT);
        assertThat(PastImportService.mapCategory("BÄCKEREI", row)).isEqualTo(IntroPickupCategory.BAKERY);
        assertThat(PastImportService.mapCategory("Messe / Grossabholung", row)).isEqualTo(IntroPickupCategory.GROSSABHOLUNG);
        assertThat(PastImportService.mapCategory(" lebensmittel-laden ", row)).isEqualTo(IntroPickupCategory.SUPERMARKET);
        assertThat(row.reasons()).isEmpty();
        assertThat(PastImportService.mapCategory("FT Reinigung", row)).isEqualTo(IntroPickupCategory.FAIRTEILER_CLEANING);
    }
}
