package network.foodsharing.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import network.foodsharing.search.service.SearchIndexer;
import network.foodsharing.search.service.SearchService;
import network.foodsharing.search.service.SearchService.SearchPermissions;
import org.junit.jupiter.api.Test;

/** A45: normalisation, query terms and the per-actor visibility matrix. */
class SearchVisibilityTest {

    private static final UUID ZURICH = UUID.randomUUID();
    private static final UUID BERLIN = UUID.randomUUID();

    @Test
    void normalisationFoldsCaseAccentsAndWhitespace() {
        assertThat(SearchIndexer.normalize("  Zürich   Café Straße ")).isEqualTo("zurich cafe strasse");
        assertThat(SearchIndexer.normalize(null)).isEmpty();
    }

    @Test
    void termsAreNormalisedDeduplicatedAndShortOnesDropped() {
        assertThat(SearchService.terms("Bäckerei a BÄCKEREI Zürich", 2)).containsExactly("backerei", "zurich");
        assertThat(SearchService.terms("a", 2)).isEmpty();
        assertThat(SearchService.terms("aa bb cc dd ee ff gg", 2)).hasSize(5);
    }

    @Test
    void visibilityMatrix() {
        SearchPermissions member = SearchPermissions.of(false, Set.of(ZURICH), Set.of());
        SearchPermissions ambassador = SearchPermissions.of(false, Set.of(ZURICH), Set.of(ZURICH));
        SearchPermissions orga = SearchPermissions.of(true, Set.of(), Set.of());
        SearchPermissions nobody = SearchPermissions.of(false, Set.of(), Set.of());

        assertThat(nobody.maySee("PUBLIC", BERLIN)).isTrue();
        assertThat(nobody.maySee("REGION_MEMBERS", ZURICH)).isFalse();
        assertThat(member.maySee("REGION_MEMBERS", ZURICH)).isTrue();
        assertThat(member.maySee("REGION_MEMBERS", BERLIN)).isFalse();
        assertThat(member.maySee("FORUM", ZURICH)).isTrue();
        assertThat(member.maySee("ADMINS", ZURICH)).isFalse();
        assertThat(ambassador.maySee("ADMINS", ZURICH)).isTrue();
        assertThat(ambassador.maySee("ADMINS", BERLIN)).isFalse();
        assertThat(orga.maySee("ADMINS", BERLIN)).isTrue();
        assertThat(orga.maySee("REGION_MEMBERS", BERLIN)).isTrue();
    }
}
