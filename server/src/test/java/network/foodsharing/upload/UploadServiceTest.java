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
import network.foodsharing.upload.service.DiskUploadStorage;
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

/** A34: size and type limits, owner-only until claimed, access through the claiming module's rule, expiry. */
@JpaSliceTest
@Import({UploadService.class, DiskUploadStorage.class, UploadServiceTest.Rules.class})
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
    void orphanVariantFilesAreRemoved() throws Exception {
        Upload kept = pdf(me);
        String orphan = "orphan/" + UUID.randomUUID();
        storage.write(kept.getStorageKey() + "_sm", new byte[] {1});
        storage.write(orphan + "_md", new byte[] {1});
        storage.write(orphan + "_original", new byte[] {1});
        java.time.Instant later = java.time.Instant.parse("2999-01-01T00:00:00Z");
        assertThat(uploads.deleteOrphanVariants(later)).isPositive();
        storage.open(kept.getStorageKey() + "_sm").close();
        storage.open(orphan + "_original").close();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.open(orphan + "_md"))
                .isInstanceOf(java.io.IOException.class);
    }
}
