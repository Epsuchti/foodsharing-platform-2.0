package network.foodsharing.upload.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.upload.domain.Upload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, UUID> {

    org.springframework.data.domain.Page<Upload> findAllByOrderByCreatedAtDesc(
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Upload> findByUsageModuleOrderByCreatedAtDesc(String module,
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Upload> findByUsageCountOrderByCreatedAtDesc(int usageCount,
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Upload> findByUsageCountGreaterThanOrderByCreatedAtDesc(int usageCount,
            org.springframework.data.domain.Pageable pageable);

    List<Upload> findByUsageModuleAndUsageCountGreaterThan(String module, int usageCount);

    long countByUsageCount(int usageCount);

    @org.springframework.data.jpa.repository.Query(
            "select u.usageModule, count(u), sum(u.size) from Upload u group by u.usageModule")
    List<Object[]> countByModule();

    List<Upload> findByVariantsFalseAndMimeTypeIn(java.util.Collection<String> mimeTypes);

    List<Upload> findByOwnerUserIdOrderByCreatedAt(java.util.UUID ownerUserId);

    boolean existsByStorageKey(String storageKey);

    List<Upload> findByUsageCountAndExpiresAtBefore(int usageCount, Instant now);
}
