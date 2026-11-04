package network.foodsharing.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.repository.UploadRepository;
import network.foodsharing.upload.repository.UploadBlobRepository;
import network.foodsharing.upload.service.DbUploadStorage;
import network.foodsharing.upload.service.UploadAccessRule;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * A34: size and type limits, owner-only until claimed, access through the claiming module's rule, expiry; bytes in the
 * database (decision 158): written with the row, rolled back with it, deleted with it.
 */
@JpaSliceTest
@Import({UploadService.class, DbUploadStorage.class, UploadBlobRepository.class, UploadServiceTest.Rules.class})
class UploadServiceTest {

    static final UUID READABLE_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
    static final UUID GONE_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000dead");

    @TestConfiguration(proxyBeanMethods = false)
    static class Rules {
        @Bean
        UploadAccessRule testRule() {
            return new UploadAccessRule() {
                @Override
                public String module() {
                    return "test";
                }

                @Override
                public boolean mayRead(UUID userId, UUID usageOwnerId) {
                    return READABLE_OWNER.equals(usageOwnerId);
                }

                @Override
                public boolean exists(UUID usageOwnerId) {
                    return !GONE_OWNER.equals(usageOwnerId);
                }
            };
        }
    }

    @Autowired
    private UploadService uploads;

    @Autowired
    private UploadRepository repository;

    @Autowired
    private UserRepository users;

    @Autowired
    private MutableClock clock;

    @Autowired
    private network.foodsharing.upload.service.UploadStorage storage;

    @Autowired
    private UploadBlobRepository blobs;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;

    private UUID me;
    private UUID other;

    @BeforeEach
    void setUp() {
        me = users.saveAndFlush(TestUsers.user("up-me@test.local", Role.FOODSAVER)).getId();
        other = users.saveAndFlush(TestUsers.user("up-other@test.local", Role.FOODSAVER)).getId();
    }

    private Upload pdf(UUID owner) {
        return uploads.store(owner, new UploadService.NewFile("../x/Report \"1\".pdf", "application/pdf; charset=binary",
                "%PDF-1.4 hello".getBytes()));
    }

    @Test
    void storesBytesAndMetadata() throws Exception {
        Upload upload = pdf(me);
        assertThat(upload.getFileName()).isEqualTo("Report 1.pdf");
        assertThat(upload.getMimeType()).isEqualTo("application/pdf");
        assertThat(upload.getSize()).isEqualTo(14);
        assertThat(upload.getSha256()).hasSize(64);
        assertThat(upload.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
        try (InputStream in = uploads.open(upload)) {
            assertThat(new String(in.readAllBytes())).isEqualTo("%PDF-1.4 hello");
        }
    }

    @Test
    void rejectsTooLargeAndDisallowedTypes() {
        byte[] big = new byte[20 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> uploads.store(me, new UploadService.NewFile("a.pdf", "application/pdf", big)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.UPLOAD_TOO_LARGE);
        assertThatThrownBy(() -> uploads.store(me, new UploadService.NewFile("a.exe", "application/x-msdownload", new byte[1])))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.UPLOAD_TYPE_NOT_ALLOWED);
        // declared image/PDF types must match the magic bytes (renamed HTML/SVG is rejected)
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertThatThrownBy(() -> uploads.store(me, new UploadService.NewFile("a.png", "image/png", html)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.UPLOAD_TYPE_NOT_ALLOWED);
        assertThatThrownBy(() -> uploads.store(me, new UploadService.NewFile("a.pdf", "application/pdf", html)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.UPLOAD_TYPE_NOT_ALLOWED);
    }

    @Test
    void magicBytesPerType() {
        assertThat(UploadService.contentMatches("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isTrue();
        assertThat(UploadService.contentMatches("image/gif", "GIF89a".getBytes())).isTrue();
        assertThat(UploadService.contentMatches("image/webp", "RIFF0000WEBPVP8 ".getBytes())).isTrue();
        assertThat(UploadService.contentMatches("image/webp", "RIFF0000WAVE".getBytes())).isFalse();
        assertThat(UploadService.contentMatches("image/png", new byte[] {(byte) 0x89, 'P'})).isFalse();
        assertThat(UploadService.contentMatches("text/plain", "anything".getBytes())).isTrue();
    }

    @Test
    void adminListingSummaryAndOwnerlessCleanup() {
        Upload unclaimed = pdf(me);
        Upload kept = uploads.claim(pdf(me).getId(), new UploadUsage("test", READABLE_OWNER));
        Upload orphan = uploads.claim(pdf(other).getId(), new UploadUsage("test", GONE_OWNER));
        assertThat(uploads.adminPage(true, null, 0, 50).getContent()).extracting(Upload::getId)
                .contains(unclaimed.getId()).doesNotContain(kept.getId());
        assertThat(uploads.adminPage(null, "test", 0, 50).getContent()).extracting(Upload::getId)
                .contains(kept.getId(), orphan.getId()).doesNotContain(unclaimed.getId());
        var summary = uploads.summary();
        assertThat(summary.unclaimedCount()).isGreaterThanOrEqualTo(1);
        assertThat(summary.byModule()).anyMatch(m -> m.module().equals("test") && m.count() >= 2);
        assertThat(uploads.deleteOwnerless()).isEqualTo(1);
        assertThat(repository.findById(orphan.getId())).isEmpty();
        assertThat(repository.findById(kept.getId())).isPresent();
    }

    @Test
    void ownerOnlyUntilClaimedThenThroughTheModuleRule() {
        Upload upload = pdf(me);
        assertThat(uploads.mayRead(me, upload)).isTrue();
        assertThat(uploads.mayRead(other, upload)).isFalse();

        Upload claimed = uploads.claim(upload.getId(), new UploadUsage("test", READABLE_OWNER));
        assertThat(claimed.getExpiresAt()).isNull();
        assertThat(claimed.getUsageCount()).isEqualTo(1);
        assertThat(uploads.mayRead(other, claimed)).isTrue();

        Upload otherUsage = uploads.claim(pdf(me).getId(), new UploadUsage("test", UUID.randomUUID()));
        assertThat(uploads.mayRead(other, otherUsage)).isFalse();
        Upload unknownModule = uploads.claim(pdf(me).getId(), new UploadUsage("nobody", READABLE_OWNER));
        assertThat(uploads.mayRead(other, unknownModule)).isFalse();
    }

    @Test
    void ownerDeletesOnlyUnclaimedUploads() {
        Upload upload = pdf(me);
        assertThatThrownBy(() -> uploads.deleteOwn(other, upload.getId()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.ACCESS_DENIED);
        Upload used = uploads.claim(pdf(me).getId(), new UploadUsage("test", READABLE_OWNER));
        assertThatThrownBy(() -> uploads.deleteOwn(me, used.getId()))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ApiErrorCode.UPLOAD_IN_USE);
        uploads.deleteOwn(me, upload.getId());
        assertThat(repository.findById(upload.getId())).isEmpty();
    }

    @Test
    void unclaimedUploadsExpire() {
        Upload unclaimed = pdf(me);
        Upload claimed = uploads.claim(pdf(me).getId(), new UploadUsage("test", READABLE_OWNER));
        assertThat(uploads.deleteExpired(clock.instant().plus(Duration.ofHours(23)))).isZero();
        assertThat(uploads.deleteExpired(clock.instant().plus(Duration.ofHours(25)))).isEqualTo(1);
        assertThat(repository.findById(unclaimed.getId())).isEmpty();
        assertThat(repository.findById(claimed.getId())).isPresent();
    }

    @Test
    void bytesGoWithTheRow() {
        Upload upload = pdf(me);
        assertThat(blobs.exists(upload.getId(), "orig")).isTrue();
        assertThat(blobs.find(upload.getId(), "orig").orElseThrow().sha256()).isEqualTo(upload.getSha256());
        uploads.delete(upload);
        repository.flush();
        assertThat(blobs.exists(upload.getId(), "orig")).isFalse();
        assertThat(blobs.chunk(upload.getId(), "orig", 0)).isEmpty();
    }

    @Test
    void aRolledBackStoreLeavesNoBytes() {
        java.util.concurrent.atomic.AtomicReference<UUID> id = new java.util.concurrent.atomic.AtomicReference<>();
        var inner = new org.springframework.transaction.support.TransactionTemplate(transactions);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> inner.executeWithoutResult(status -> {
            // system upload (no owner): the outer test transaction's users are invisible here
            id.set(uploads.store(null, new UploadService.NewFile("a.pdf", "application/pdf",
                    "%PDF-1.4 x".getBytes())).getId());
            assertThat(blobs.exists(id.get(), "orig")).isTrue();
            throw new IllegalStateException("roll back");
        })).hasMessage("roll back");
        Boolean blobLeft = inner.execute(status -> blobs.exists(id.get(), "orig"));
        Boolean rowLeft = inner.execute(status -> repository.existsById(id.get()));
        assertThat(blobLeft).isFalse();
        assertThat(rowLeft).isFalse();
    }

    /** A row without variants whose variant blobs exist is marked; without them its variants are rendered. */
    @Test
    void missingVariantsAreReusedWhenStoredElseRendered() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(400, 300,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        Upload upload = uploads.store(me, new UploadService.NewFile("a.png", "image/png", png.toByteArray()));
        assertThat(upload.isVariants()).isTrue();
        byte[] marker = {1, 2, 3};
        storage.write(upload.getId(), "md", "image/jpeg", new java.io.ByteArrayInputStream(marker));

        upload.setVariants(false);
        assertThat(uploads.renderMissingVariants()).isGreaterThanOrEqualTo(1);
        assertThat(upload.isVariants()).isTrue();
        try (InputStream in = storage.open(upload.getId(), "md").orElseThrow().stream()) {
            assertThat(in.readAllBytes()).isEqualTo(marker); // reused, not rendered again
        }

        Upload bare = uploads.store(me, new UploadService.NewFile("b.png", "image/png", png.toByteArray()));
        assertThat(uploads.restoreBytes(bare.getId(), png.toByteArray(), java.util.Map.of()))
                .containsOnlyKeys("sm", "md", "lg"); // rendered
        try (InputStream in = storage.open(bare.getId(), "sm").orElseThrow().stream()) {
            assertThat(in.readAllBytes()).startsWith((byte) 0xFF, (byte) 0xD8);
        }
        UploadService.Download md = uploads.open(bare, "md");
        assertThat(md.mimeType()).isEqualTo("image/jpeg");
        assertThat(md.sha256()).isNotEqualTo(bare.getSha256());
        assertThat(uploads.open(bare, null).sha256()).isEqualTo(bare.getSha256());
    }

    @Test
    void restoreBytesUsesGivenVariantsWhenComplete() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(40, 30,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        Upload upload = uploads.store(me, new UploadService.NewFile("c.png", "image/png", png.toByteArray()));
        upload.setWidth(null); // a seeded row without dimensions: they come from the image header
        upload.setHeight(null);
        java.util.Map<String, byte[]> given = java.util.Map.of("sm", new byte[] {1}, "md", new byte[] {2},
                "lg", new byte[] {3});
        assertThat(uploads.restoreBytes(upload.getId(), png.toByteArray(), given)).isSameAs(given);
        assertThat(upload.getWidth()).isEqualTo(40);
        assertThat(upload.getHeight()).isEqualTo(30);
        try (InputStream in = uploads.open(upload, "lg").stream()) {
            assertThat(in.readAllBytes()).containsExactly(3);
        }
    }
}
