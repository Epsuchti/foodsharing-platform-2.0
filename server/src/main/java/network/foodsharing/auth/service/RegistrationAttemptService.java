package network.foodsharing.auth.service;

import java.util.LinkedHashMap;
import java.util.Map;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.RegistrationAttempt;
import network.foodsharing.auth.domain.RegistrationOutcome;
import network.foodsharing.auth.repository.RegistrationAttemptRepository;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registration attempt tracking (parity {@code registration_attempt}); email and IP are stored as hashes only. */
@Service
public class RegistrationAttemptService {

    private final RegistrationAttemptRepository repository;
    private final HistoryService history;
    private final IpHasher ipHasher;

    public RegistrationAttemptService(RegistrationAttemptRepository repository, HistoryService history,
                                      IpHasher ipHasher) {
        this.repository = repository;
        this.history = history;
        this.ipHasher = ipHasher;
    }

    /**
     * Records a blocked sign-up and REGISTRATION_BLOCKED {provider, reason, emailDomain} in its own transaction,
     * so both survive the rollback caused by the error the caller throws next.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlocked(String email, String clientIp, IdentityProvider provider, RegistrationOutcome outcome,
                              ApiErrorCode reason) {
        save(email, clientIp, provider, outcome);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", provider.name());
        metadata.put("reason", reason.getValue());
        metadata.put("emailDomain", domainOf(email));
        history.record(history.event(AuthAction.REGISTRATION_BLOCKED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(metadata).build());
    }

    /** Records a successful sign-up (the account creation itself is recorded as UserAction.ACCOUNT_CREATED). */
    @Transactional
    @NoHistory(reason = "ACCOUNT_CREATED is recorded by UserService.create in the same transaction")
    public void recordCreated(String email, String clientIp, IdentityProvider provider) {
        save(email, clientIp, provider, RegistrationOutcome.CREATED);
    }

    private void save(String email, String clientIp, IdentityProvider provider, RegistrationOutcome outcome) {
        RegistrationAttempt attempt = new RegistrationAttempt();
        attempt.setEmailHash(TokenHasher.sha256Hex(email));
        attempt.setIpHash(ipHasher.hash(clientIp));
        attempt.setProvider(provider);
        attempt.setOutcome(outcome);
        repository.save(attempt);
    }

    static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1);
    }
}
