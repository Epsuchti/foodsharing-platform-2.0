package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Decision 153, S17 D5: the linked store's city, else the region's name, nothing when online. */
class IntroPickupCitiesTest {

    @Test
    void theStoresCityWins() {
        assertThat(IntroPickupCities.city(false, "Winterthur", "Zürich Stadt")).isEqualTo("Winterthur");
    }

    @Test
    void elseTheRegionsName() {
        assertThat(IntroPickupCities.city(false, null, "Zürich Stadt")).isEqualTo("Zürich Stadt");
        assertThat(IntroPickupCities.city(false, " ", "Zürich Stadt")).isEqualTo("Zürich Stadt");
    }

    @Test
    void nothingWhenOnline() {
        assertThat(IntroPickupCities.city(true, "Winterthur", "Zürich Stadt")).isNull();
    }
}
