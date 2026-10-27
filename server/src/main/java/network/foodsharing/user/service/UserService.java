package network.foodsharing.user.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user module's API for other modules. */
@Service
public class UserService {

    private final UserRepository repository;
    private final CurrentActorService currentActorService;
    private final HistoryService history;
    private final Clock clock;

    public UserService(UserRepository repository, CurrentActorService currentActorService, HistoryService history,
                       Clock clock) {
        this.repository = repository;
        this.currentActorService = currentActorService;
        this.history = history;
        this.clock = clock;
    }

    /** @throws ApiException 401 when not signed in, 404 USER_NOT_FOUND when the account is gone or disabled */
    @Transactional(readOnly = true)
    public User getCurrent() {
        UUID userId = currentActorService.requireUserId();
        return findActive(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<User> findActive(UUID userId) {
        return repository.findById(userId).filter(User::isActive);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(normalizeEmail(email));
    }

    /** Records one SETTINGS_CHANGED entry per changed setting (field, old, new). */
    @Transactional
    public User updateSettings(SettingsChange change) {
        User user = getCurrentForUpdate();
        if (change.language() != null && change.language() != user.getPreferredLanguage()) {
            history.record(history.event(UserAction.SETTINGS_CHANGED, user.getId())
                    .change("preferredLanguage", user.getPreferredLanguage(), change.language()).build());
            user.setPreferredLanguage(change.language());
        }
        if (change.theme() != null && change.theme() != user.getTheme()) {
            history.record(history.event(UserAction.SETTINGS_CHANGED, user.getId())
                    .change("theme", user.getTheme(), change.theme()).build());
            user.setTheme(change.theme());
        }
        return user;
    }

    /**
     * Creates an account (role FOODSHARER, next foodsaver number) and records ACCOUNT_CREATED. The caller has
     * checked that the email is free and allowed.
     */
    @Transactional
    public User create(NewUser newUser) {
        User user = new User();
        user.setEmail(normalizeEmail(newUser.email()));
        user.setEmailVerifiedAt(newUser.emailVerifiedAt());
        user.setDisplayName(newUser.displayName());
        user.setFirstName(newUser.firstName());
        user.setLastName(newUser.lastName());
        user.setPreferredLanguage(newUser.language() == null ? Language.DE : newUser.language());
        user.setAvatarUrl(newUser.avatarUrl());
        user.setRole(Role.FOODSHARER);
        user.setActive(true);
        user.setRegisteredAt(clock.instant());
        user.setFoodsaverNumber(repository.nextFoodsaverNumber());
        User saved = repository.save(user);
        history.record(history.event(UserAction.ACCOUNT_CREATED, saved.getId())
                .metadata("language", saved.getPreferredLanguage().name()).build());
        return saved;
    }

    /** Changes the global role and records ROLE_CHANGED. Permission checks are the caller's job. */
    @Transactional
    public User changeRole(UUID userId, Role role) {
        User user = repository.findById(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        if (user.getRole() != role) {
            history.record(history.event(UserAction.ROLE_CHANGED, user.getId())
                    .change("role", user.getRole(), role).build());
            user.setRole(role);
        }
        return user;
    }

    /** Sets last_login_at. */
    @Transactional
    @NoHistory(reason = "the login itself is recorded as AuthAction.LOGIN by AuthSessionService.create")
    public void markLoggedIn(UUID userId) {
        repository.findById(userId).ifPresent(user -> user.setLastLoginAt(clock.instant()));
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
    }

    private User getCurrentForUpdate() {
        UUID userId = currentActorService.requireUserId();
        return findActive(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
    }
}
