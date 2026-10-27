package network.foodsharing.user.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.UserNote;
import network.foodsharing.user.domain.UserNoteKind;
import network.foodsharing.user.repository.UserNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notes about users (e.g. the required verification message until messaging exists in M5). Permission checks are the
 * caller's job; the caller also records the history of the action the note belongs to.
 */
@Service
public class UserNoteService {

    private final UserNoteRepository repository;
    private final CurrentActorService currentActorService;

    public UserNoteService(UserNoteRepository repository, CurrentActorService currentActorService) {
        this.repository = repository;
        this.currentActorService = currentActorService;
    }

    @Transactional
    @NoHistory(reason = "the action the note belongs to records its history (e.g. VERIFIED with the message as reason)")
    public UserNote add(UUID userId, UserNoteKind kind, String text) {
        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setKind(kind);
        note.setText(text);
        note.setAuthorId(currentActorService.currentUserId().orElse(null));
        return repository.save(note);
    }

    @Transactional(readOnly = true)
    public List<UserNote> list(UUID userId, UserNoteKind kind) {
        return kind == null ? repository.findByUserIdOrderByCreatedAtDesc(userId)
                : repository.findByUserIdAndKindOrderByCreatedAtDesc(userId, kind);
    }
}
