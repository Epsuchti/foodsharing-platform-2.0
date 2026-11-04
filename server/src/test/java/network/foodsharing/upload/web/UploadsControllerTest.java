package network.foodsharing.upload.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Decision 158: strong ETag = quoted sha256, If-None-Match matching, Cache-Control per public/private upload. */
class UploadsControllerTest {

    private static final String SHA = "ab".repeat(32);
    private static final String ETAG = "\"" + SHA + "\"";

    @Test
    void etagIsTheQuotedSha256() {
        assertThat(UploadsController.etag(SHA)).isEqualTo(ETAG);
    }

    @Test
    void ifNoneMatchDecidesNotModified() {
        assertThat(UploadsController.notModified(null, ETAG)).isFalse();
        assertThat(UploadsController.notModified("", ETAG)).isFalse();
        assertThat(UploadsController.notModified(ETAG, ETAG)).isTrue();
        assertThat(UploadsController.notModified("W/" + ETAG, ETAG)).isTrue();
        assertThat(UploadsController.notModified("\"other\", " + ETAG, ETAG)).isTrue();
        assertThat(UploadsController.notModified("*", ETAG)).isTrue();
        assertThat(UploadsController.notModified("\"other\"", ETAG)).isFalse();
        assertThat(UploadsController.notModified(SHA, ETAG)).isFalse(); // unquoted is not the tag
    }

    @Test
    void cacheControlPerVisibility() {
        assertThat(UploadsController.cacheControl(true)).isEqualTo("public, max-age=604800, immutable");
        assertThat(UploadsController.cacheControl(false)).isEqualTo("private, no-cache");
    }
}
