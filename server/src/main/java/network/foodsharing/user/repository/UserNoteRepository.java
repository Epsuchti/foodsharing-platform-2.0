package network.foodsharing.user.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.user.domain.UserNote;
import network.foodsharing.user.domain.UserNoteKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNoteRepository extends JpaRepository<UserNote, UUID> {

    List<UserNote> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    List<UserNote> findByUserIdAndKindOrderByCreatedAtDescIdDesc(UUID userId, UserNoteKind kind);

    long countByUserIdAndKind(UUID userId, UserNoteKind kind);
}
