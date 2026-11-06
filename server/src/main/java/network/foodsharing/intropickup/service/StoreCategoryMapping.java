package network.foodsharing.intropickup.service;

import java.util.Locale;
import java.util.Map;
import network.foodsharing.intropickup.domain.IntroPickupCategory;

/**
 * EA16 (decision 169): store categories are editable rows, EinAb categories an enum, so the pre-fill of an EinAb
 * from a store pickup maps the store category by name. Unknown or missing names give SUPERMARKET; the guide can
 * change the result in the form.
 */
public final class StoreCategoryMapping {

    private static final Map<String, IntroPickupCategory> BY_NAME = Map.of(
            "supermarkt", IntroPickupCategory.SUPERMARKET,
            "bio-laden", IntroPickupCategory.SUPERMARKET,
            "bäckerei", IntroPickupCategory.BAKERY,
            "restaurant", IntroPickupCategory.RESTAURANT,
            "hotel", IntroPickupCategory.RESTAURANT,
            "markt", IntroPickupCategory.MARKET,
            "take-away", IntroPickupCategory.TAKEOUT,
            "grossverteiler", IntroPickupCategory.GROSSABHOLUNG);

    private StoreCategoryMapping() {
    }

    /** The EinAb category for a store category name (case and surrounding blanks ignored). */
    public static IntroPickupCategory of(String storeCategoryName) {
        if (storeCategoryName == null || storeCategoryName.isBlank()) {
            return IntroPickupCategory.SUPERMARKET;
        }
        return BY_NAME.getOrDefault(storeCategoryName.strip().toLowerCase(Locale.ROOT),
                IntroPickupCategory.SUPERMARKET);
    }
}
