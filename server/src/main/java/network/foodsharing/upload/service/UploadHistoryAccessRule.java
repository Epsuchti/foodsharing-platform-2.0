package network.foodsharing.upload.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.repository.UploadRepository;
import org.springframework.stereotype.Component;

/** Upload history: the owner (ORGA always, via the history module). */
@Component
public class UploadHistoryAccessRule implements HistoryAccessRule {

    private final UploadRepository repository;

    public UploadHistoryAccessRule(UploadRepository repository) {
        this.repository = repository;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.UPLOAD;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return repository.findById(subjectId).map(upload -> actor.userId().equals(upload.getOwnerUserId())).orElse(false);
    }
}
