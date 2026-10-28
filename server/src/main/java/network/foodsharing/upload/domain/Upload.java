package network.foodsharing.upload.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * An uploaded file (A34). Bytes live in the {@code UploadStorage} under {@link #storageKey}; until a module claims it
 * ({@link #usageCount} &gt; 0) only the owner may read it and it expires at {@link #expiresAt}.
 */
@Getter
@Setter
@Entity
@Table(name = "upload")
public class Upload extends BaseEntity {

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "size", nullable = false)
    private long size;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    /** Module that claimed the upload (decides read access through its {@code UploadAccessRule}). */
    @Column(name = "usage_module", length = 40)
    private String usageModule;

    /** Id of the owning object in that module (e.g. the mail message). */
    @Column(name = "usage_owner_id")
    private UUID usageOwnerId;

    /** Unclaimed uploads are deleted after this instant; {@code null} once claimed. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Pixel size of images (A41); null for other files. */
    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    /** Whether the sm/md/lg variants exist in the store as {@code <storage_key>_<size>}. */
    @Column(name = "variants", nullable = false)
    private boolean variants;

    /** Claimed by a public usage (USER_PHOTO, FSP_PHOTO, …): readable without login. */
    @Column(name = "is_public", nullable = false)
    private boolean publicReadable;
}
