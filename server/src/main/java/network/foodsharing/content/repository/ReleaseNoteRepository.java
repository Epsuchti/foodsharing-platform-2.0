package network.foodsharing.content.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.content.domain.ReleaseNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, UUID> {

    List<ReleaseNote> findAllByOrderByReleasedAtDescReleaseVersionDesc();

    boolean existsByReleaseVersion(String releaseVersion);
}
