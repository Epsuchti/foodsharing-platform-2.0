package network.foodsharing.dev.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;

/** The catalog, the test fixture {@link SeedUsers} and the seed CSV agree on ids, names and order. */
class DevUserCatalogTest {

    private final DevUserCatalog catalog = new DevUserCatalog();

    @Test
    void catalogMatchesSeedUsersInJourneyOrder() {
        assertThat(catalog.entries()).extracting(DevUserCatalog.Entry::id)
                .containsExactlyElementsOf(Arrays.stream(SeedUsers.values()).map(SeedUsers::id).toList());
        assertThat(catalog.entries()).extracting(DevUserCatalog.Entry::email)
                .containsExactlyElementsOf(Arrays.stream(SeedUsers.values()).map(SeedUsers::email).toList());
        assertThat(catalog.byId(SeedUsers.ORGA.id())).get().extracting(DevUserCatalog.Entry::descriptionKey)
                .isEqualTo("dev.users.orga");
    }

    @Test
    void onlyFoodsaverAndBotHaveGoogleIdentities() {
        assertThat(catalog.entries()).filteredOn(entry -> entry.googleSubject() != null)
                .extracting(DevUserCatalog.Entry::googleSubject)
                .containsExactly("fake-google-foodsaver", "fake-google-bot");
        assertThat(catalog.byGoogleSubject("fake-google-bot")).get().extracting(DevUserCatalog.Entry::name)
                .isEqualTo("bot");
        assertThat(catalog.byGoogleSubject(null)).isEmpty();
    }

    @Test
    void seedCsvContainsEveryCatalogUser() throws IOException {
        List<String> lines;
        try (InputStream in = getClass().getResourceAsStream("/db/seed/user/user_account.csv")) {
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).toList();
        }
        assertThat(lines).hasSize(catalog.entries().size());
        for (int i = 0; i < lines.size(); i++) {
            DevUserCatalog.Entry entry = catalog.entries().get(i);
            assertThat(lines.get(i)).startsWith(entry.id() + ",").contains("," + entry.email() + ",")
                    .contains("/images/avatars/" + entry.name() + ".svg");
        }
    }
}
