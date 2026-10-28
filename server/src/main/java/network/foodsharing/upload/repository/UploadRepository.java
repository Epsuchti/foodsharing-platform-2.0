package network.foodsharing.upload.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.upload.domain.Upload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, UUID> {

    List<Upload> findByUsageCountAndExpiresAtBefore(int usageCount, Instant now);
}
