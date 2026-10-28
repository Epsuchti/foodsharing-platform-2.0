package network.foodsharing.user.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.error.ApiFieldError;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.ContactVisibility;
import network.foodsharing.user.domain.SleepRules;
import network.foodsharing.user.domain.SleepStatus;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user module's API for other modules. */
@Service
public class UserService {

    private static final java.time.ZoneId ZURICH = java.time.ZoneId.of("Europe/Zurich");

    private static final String REDACTED = "***";

    private final UserRepository repository;
    private final CurrentActorService currentActorService;
    private final HistoryService history;
    private final Clock clock;
    private final PermissionService permissionService;
    private final ObjectProvider<RegionDirectory> regionDirectory;

    public UserService(UserRepository repository, CurrentActorService currentActorService, HistoryService history,
                       Clock clock, PermissionService permissionService, ObjectProvider<RegionDirectory> regionDirectory) {
        this.repository = repository;
        this.currentActorService = currentActorService;
        this.history = history;
        this.clock = clock;
        this.permissionService = permissionService;
        this.regionDirectory = regionDirectory;
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

    /** Active users by id (missing / inactive ids are skipped). */
    @Transactional(readOnly = true)
    public List<User> findActive(Collection<UUID> userIds) {
        return userIds.isEmpty() ? List.of()
                : repository.findByIdIn(userIds).stream().filter(User::isActive).toList();
    }

    /** Users by id, including disabled ones (display of past data). */
    @Transactional(readOnly = true)
    public List<User> findAll(Collection<UUID> userIds) {
        return userIds.isEmpty() ? List.of() : repository.findByIdIn(userIds);
    }

    /** Number of active verified users (explainer counter). */
    @Transactional(readOnly = true)
    public long countVerifiedActive() {
        return repository.countByVerifiedTrueAndActiveTrue();
    }

    /** Active users who are not verified yet (onboarding nudges). */
    @Transactional(readOnly = true)
    public List<User> findActiveUnverified() {
        return repository.findByVerifiedFalseAndActiveTrue();
    }

    /** Users by foodsaver number (CSV imports), including disabled ones. */
    @Transactional(readOnly = true)
    public List<User> findByFoodsaverNumbers(Collection<Long> numbers) {
        return numbers.isEmpty() ? List.of() : repository.findByFoodsaverNumberIn(numbers);
    }

    /** Active users whose home region is one of the given regions. */
    @Transactional(readOnly = true)
    public List<User> findActiveByHomeRegions(Collection<UUID> regionIds) {
        return regionIds.isEmpty() ? List.of() : repository.findByHomeRegionIdInAndActiveTrue(regionIds);
    }

    /** One-click unsubscribe (N7, no session); @return whether the user was opted in. */
    @Transactional
    @network.foodsharing.history.NoHistory(reason = "recorded as NEWSLETTER_OPT_OUT by the newsletter module")
    public boolean optOutOfNewsletter(UUID userId) {
        User user = repository.findById(userId).orElse(null);
        if (user == null || !user.isNewsletterOptIn()) {
            return false;
        }
        user.setNewsletterOptIn(false);
        return true;
    }

    /** Active users who opted in to newsletters (N7). */
    @Transactional(readOnly = true)
    public List<User> findActiveInfoMailRecipients() {
        return repository.findByActiveTrueAndInfoMailMessageTrueAndDeletedAtIsNull();
    }

    @Transactional(readOnly = true)
    public List<User> findActiveNewsletterRecipients() {
        return repository.findByActiveTrueAndNewsletterOptInTrue();
    }

    /** Active users with exactly this role (e.g. ORGA as the last bell fallback). */
    @Transactional(readOnly = true)
    public List<User> findActiveByRole(Role role) {
        return repository.findByRoleAndActiveTrue(role);
    }

    /** @throws ApiException 404 USER_NOT_FOUND (also returns inactive users; callers check permissions) */
    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return repository.findById(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
    }

    /** @throws ApiException 404 USER_NOT_FOUND when missing or disabled */
    @Transactional(readOnly = true)
    public User requireActive(UUID userId) {
        return findActive(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
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
        if (change.pickupRemindersEnabled() != null
                && change.pickupRemindersEnabled() != user.isPickupRemindersEnabled()) {
            history.record(history.event(UserAction.PICKUP_REMINDERS_CHANGED, user.getId())
                    .change("pickupRemindersEnabled", user.isPickupRemindersEnabled(), change.pickupRemindersEnabled())
                    .build());
            user.setPickupRemindersEnabled(change.pickupRemindersEnabled());
        }
        return user;
    }

    /** Newsletter opt-in of the current user (N7); one SETTINGS_CHANGED entry when it changes. */
    @Transactional
    public User setNewsletterOptIn(boolean optIn) {
        User user = getCurrentForUpdate();
        if (user.isNewsletterOptIn() != optIn) {
            history.record(history.event(UserAction.SETTINGS_CHANGED, user.getId())
                    .change("newsletterOptIn", user.isNewsletterOptIn(), optIn).build());
            user.setNewsletterOptIn(optIn);
        }
        return user;
    }

    /**
     * Changes the current user's own profile (plan §8.3). One PROFILE_UPDATED entry per changed field
     * (PHONE_CHANGED for the mobile phone, HOME_REGION_CHANGED for the home region; phone numbers and the date of
     * birth are redacted). Phone numbers are stored in E.164 form.
     *
     * @throws ApiException 400 INVALID_PHONE_NUMBER / VALIDATION_FAILED, 404 REGION_NOT_FOUND, 409 NOT_A_MEMBER
     */
    @Transactional
    public User updateMyProfile(ProfileChange change) {
        User user = getCurrentForUpdate();
        checkHomeRegion(user, change.homeRegionId());
        applyProfile(user, change, UserAction.PROFILE_UPDATED, null);
        return user;
    }

    /**
     * An administrator changes admin-editable fields (reason required); PROFILE_UPDATED_BY_ADMIN per field.
     *
     * @throws ApiException 403 unless mayAdministrateUser, 404 USER_NOT_FOUND, 400 INVALID_PHONE_NUMBER,
     *                      409 NOT_A_MEMBER (home region)
     */
    @Transactional
    public User updateProfileAsAdmin(UUID userId, ProfileChange change, String reason) {
        permissionService.requireMayAdministrateUser(currentActorService.requirePrincipal(), userId);
        User user = require(userId);
        String requiredReason = requireReason(reason);
        checkHomeRegion(user, change.homeRegionId());
        applyProfile(user, change, UserAction.PROFILE_UPDATED_BY_ADMIN, requiredReason);
        return user;
    }

    /**
     * Enables or disables an account; a disabled account's sessions stop working at once (the session filter only
     * accepts active users). Records ACTIVATED / DEACTIVATED with the reason.
     *
     * @throws ApiException 403 unless mayAdministrateUser, 404 USER_NOT_FOUND, 409 CANNOT_DEACTIVATE_SELF
     */
    @Transactional
    public User setActive(UUID userId, boolean active, String reason) {
        SessionPrincipal actor = currentActorService.requirePrincipal();
        permissionService.requireMayAdministrateUser(actor, userId);
        User user = require(userId);
        String requiredReason = requireReason(reason);
        if (!active && actor.userId().equals(userId)) {
            throw ApiException.conflict(ApiErrorCode.CANNOT_DEACTIVATE_SELF);
        }
        if (user.isActive() != active) {
            history.record(history.event(active ? UserAction.ACTIVATED : UserAction.DEACTIVATED, userId)
                    .change("active", user.isActive(), active).reason(requiredReason).build());
            user.setActive(active);
        }
        return user;
    }

    /**
     * Sets the home region and records HOME_REGION_CHANGED. Membership and region type are checked by the caller
     * (region module).
     */
    @Transactional
    public User changeHomeRegion(UUID userId, UUID regionId) {
        User user = require(userId);
        if (!Objects.equals(user.getHomeRegionId(), regionId)) {
            history.record(history.event(UserAction.HOME_REGION_CHANGED, userId)
                    .change("homeRegionId", user.getHomeRegionId(), regionId).region(regionId).build());
            user.setHomeRegionId(regionId);
        }
        return user;
    }

    /** Is the user sleeping today (A39, Europe/Zurich calendar day)? */
    public boolean isSleeping(User user) {
        return user.isSleeping(today());
    }

    /** Today in Europe/Zurich (sleep mode, statistics). */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZURICH);
    }

    /**
     * Sets the current user's sleep mode (A39). NONE and FULL clear the dates; TEMPORARY needs
     * {@code from ≤ until} and {@code until ≥ today}. Records SLEEP_MODE_CHANGED when something changed.
     *
     * @throws ApiException 400 SLEEP_DATES_INVALID
     */
    @Transactional
    public User updateSleepMode(SleepStatus status, LocalDate from, LocalDate until, String message) {
        User user = getCurrentForUpdate();
        if (!SleepRules.validDates(status, from, until, today())) {
            throw ApiException.badRequest(ApiErrorCode.SLEEP_DATES_INVALID);
        }
        LocalDate newFrom = status == SleepStatus.TEMPORARY ? from : null;
        LocalDate newUntil = status == SleepStatus.TEMPORARY ? until : null;
        String newMessage = message == null || message.isBlank() ? null : message.strip();
        if (user.getSleepStatus() == status && Objects.equals(user.getSleepFrom(), newFrom)
                && Objects.equals(user.getSleepUntil(), newUntil) && Objects.equals(user.getSleepMessage(), newMessage)) {
            return user;
        }
        history.record(history.event(UserAction.SLEEP_MODE_CHANGED, user.getId())
                .change("sleepStatus", user.getSleepStatus(), status)
                .change("sleepFrom", user.getSleepFrom(), newFrom)
                .change("sleepUntil", user.getSleepUntil(), newUntil).build());
        user.setSleepStatus(status);
        user.setSleepFrom(newFrom);
        user.setSleepUntil(newUntil);
        user.setSleepMessage(newMessage);
        return user;
    }

    /** Maintenance (A13): accounts without login since {@code cutoff} that were not warned since their last login. */
    @Transactional(readOnly = true)
    public List<User> inactiveUnwarned(Instant cutoff) {
        return repository.findInactiveUnwarned(cutoff);
    }

    /** Maintenance (A13): the inactive-account warning mail went out (actor SYSTEM). */
    @Transactional
    public void markInactivityWarned(UUID userId) {
        User user = repository.findById(userId).orElseThrow();
        history.record(history.event(UserAction.INACTIVITY_WARNED, userId)
                .metadata("lastLoginAt", String.valueOf(user.getLastLoginAt())).build());
        user.setInactivityWarnedAt(clock.instant());
    }

    /** Maintenance (A39): TEMPORARY sleep modes whose period ended go back to NONE (actor SYSTEM). */
    @Transactional
    public int wakeUpExpiredSleepers() {
        LocalDate today = today();
        int woken = 0;
        for (User user : repository.findBySleepStatus(SleepStatus.TEMPORARY)) {
            if (SleepRules.expired(user.getSleepStatus(), user.getSleepUntil(), today)) {
                history.record(history.event(UserAction.SLEEP_MODE_CHANGED, user.getId())
                        .change("sleepStatus", user.getSleepStatus(), SleepStatus.NONE).reason("sleep period ended")
                        .build());
                user.setSleepStatus(SleepStatus.NONE);
                user.setSleepFrom(null);
                user.setSleepUntil(null);
                woken++;
            }
        }
        return woken;
    }

    /** Keeps the banana and buddy counters of the profile statistics in sync (profile module). */
    @Transactional
    @NoHistory(reason = "derived counters; the profile module records BANANA_* / BUDDY_* for the change itself")
    public void setCommunityCounts(UUID userId, Integer bananaCount, Integer buddyCount) {
        repository.findById(userId).ifPresent(user -> {
            if (bananaCount != null) {
                user.setStatBananaCount(bananaCount);
            }
            if (buddyCount != null) {
                user.setStatBuddyCount(buddyCount);
            }
        });
    }

    /** May the current user see this user's phone numbers (PUBLIC visibility, self, or administrators)? */
    @Transactional(readOnly = true)
    public boolean mayCurrentUserSeeContact(User user) {
        Optional<SessionPrincipal> principal = currentActorService.principal();
        if (principal.isEmpty()) {
            return false;
        }
        return user.getContactVisibility() == ContactVisibility.PUBLIC
                || principal.get().userId().equals(user.getId())
                || permissionService.mayAdministrateUser(principal.get(), user.getId());
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

    /** Bounce processing (A35): flags the user whose address bounced; returns the user id when one matches. */
    @Transactional
    @NoHistory(reason = "the mailbox module records MailboxAction.BOUNCE_RECORDED with the user as secondary subject")
    public java.util.Optional<UUID> markEmailBounced(String email, java.time.Instant at) {
        return repository.findByEmailIgnoreCase(email == null ? "" : email.strip()).map(user -> {
            user.setEmailBouncedAt(at);
            return user.getId();
        });
    }

    /** Completes an email change (A5): new address, verified now, bounce flag cleared. */
    @Transactional
    @NoHistory(reason = "the auth module's EmailChangeService records UserAction.EMAIL_CHANGE_COMPLETED")
    public User changeEmail(UUID userId, String newEmail, java.time.Instant verifiedAt) {
        User user = repository.findById(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        user.setEmail(normalizeEmail(newEmail));
        user.setEmailVerifiedAt(verifiedAt);
        user.setEmailBouncedAt(null);
        return user;
    }

    /** Links or unlinks the personal mailbox (MB6); the mailbox module records MAILBOX_CREATED / MAILBOX_DELETED. */
    @Transactional
    @NoHistory(reason = "the mailbox module records MailboxAction.MAILBOX_CREATED / MAILBOX_DELETED")
    public User setMailboxId(UUID userId, UUID mailboxId) {
        User user = repository.findById(userId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.USER_NOT_FOUND));
        user.setMailboxId(mailboxId);
        return user;
    }

    /** Verified flag and date; the verification module records VERIFIED / UNVERIFIED. */
    @Transactional
    @NoHistory(reason = "the caller records VerificationAction.VERIFIED / UNVERIFIED with the required message")
    public User setVerified(UUID userId, boolean verified, java.time.Instant at) {
        User user = require(userId);
        user.setVerified(verified);
        user.setVerifiedAt(verified ? at : null);
        return user;
    }

    /** Identity confirmation (null = reverted); the verification module records the history. */
    @Transactional
    @NoHistory(reason = "the caller records IDENTITY_CONFIRMED / IDENTITY_CONFIRMATION_REVERTED")
    public User setIdentityConfirmation(UUID userId, java.time.Instant at, UUID by) {
        User user = require(userId);
        user.setIdentityConfirmedAt(at);
        user.setIdentityConfirmedBy(by);
        return user;
    }

    /** Start of the current passport validity; the passport module records the history. */
    @Transactional
    @NoHistory(reason = "the caller records PASSPORT_GENERATED / PASSPORT_GENERATED_BY_AMBASSADOR")
    public User setLastPassportAt(UUID userId, java.time.Instant at) {
        User user = require(userId);
        user.setLastPassportAt(at);
        return user;
    }

    /** Sets the quiz role (plan §9 {@code quiz_rolle}); the quiz module records QUIZ_ROLE_RAISED. */
    @Transactional
    @NoHistory(reason = "the caller records QuizSessionAction.QUIZ_ROLE_RAISED")
    public User setQuizRole(UUID userId, Role quizRole) {
        User user = require(userId);
        user.setQuizRole(quizRole);
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

    private void checkHomeRegion(User user, UUID regionId) {
        if (regionId == null || regionId.equals(user.getHomeRegionId())) {
            return;
        }
        RegionDirectory directory = regionDirectory.getIfAvailable();
        if (directory == null || directory.find(regionId).isEmpty()) {
            throw ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND);
        }
        if (!directory.isActiveMember(user.getId(), regionId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_A_MEMBER);
        }
        if (!directory.isAccessible(regionId)) {
            throw ApiException.conflict(ApiErrorCode.REGION_NOT_JOINABLE);
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                    List.of(new ApiFieldError("reason", "NotBlank", "a reason is required")), Map.of());
        }
        return reason.strip();
    }

    @FunctionalInterface
    private interface Recorder {
        void record(String field, Object oldValue, Object newValue, boolean redact, UserAction specificAction);
    }

    private void applyProfile(User user, ProfileChange change, UserAction action, String reason) {
        List<ApiFieldError> phoneErrors = new ArrayList<>();
        String mobile = phone("mobilePhone", change.mobilePhone(), phoneErrors);
        String landline = phone("landlinePhone", change.landlinePhone(), phoneErrors);
        if (!phoneErrors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PHONE_NUMBER, phoneErrors, Map.of());
        }
        if (change.displayName() != null && change.displayName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                    List.of(new ApiFieldError("displayName", "NotBlank", "display name must not be blank")), Map.of());
        }
        UUID userId = user.getId();
        boolean own = action == UserAction.PROFILE_UPDATED;
        Recorder recorder = (field, oldValue, newValue, redact, specificAction) -> history.record(
                history.event(specificAction == null ? action : specificAction, userId)
                        .change(field, redact && oldValue != null ? REDACTED : oldValue,
                                redact && newValue != null ? REDACTED : newValue)
                        .reason(reason).build());
        text(change.firstName(), user.getFirstName(), user::setFirstName, "firstName", recorder);
        text(change.lastName(), user.getLastName(), user::setLastName, "lastName", recorder);
        text(change.displayName(), user.getDisplayName(), user::setDisplayName, "displayName", recorder);
        value(change.gender(), user.getGender(), user::setGender, "gender", false, recorder);
        value(change.dateOfBirth(), user.getDateOfBirth(), user::setDateOfBirth, "dateOfBirth", true, recorder);
        if (change.mobilePhone() != null && !Objects.equals(mobile, user.getMobilePhone())) {
            recorder.record("mobilePhone", user.getMobilePhone(), mobile, true, own ? UserAction.PHONE_CHANGED : null);
            user.setMobilePhone(mobile);
        }
        if (change.landlinePhone() != null && !Objects.equals(landline, user.getLandlinePhone())) {
            recorder.record("landlinePhone", user.getLandlinePhone(), landline, true, null);
            user.setLandlinePhone(landline);
        }
        text(change.homepage(), user.getHomepage(), user::setHomepage, "homepage", recorder);
        value(change.contactVisibility(), user.getContactVisibility(), user::setContactVisibility,
                "contactVisibility", false, recorder);
        text(change.street(), user.getStreet(), user::setStreet, "street", recorder);
        text(change.postcode(), user.getPostcode(), user::setPostcode, "postcode", recorder);
        text(change.city(), user.getCity(), user::setCity, "city", recorder);
        text(change.country() == null ? null : change.country().toUpperCase(Locale.ROOT), user.getCountry(),
                user::setCountry, "country", recorder);
        value(change.latitude(), user.getLatitude(), user::setLatitude, "latitude", false, recorder);
        value(change.longitude(), user.getLongitude(), user::setLongitude, "longitude", false, recorder);
        text(change.aboutMePublic(), user.getAboutMePublic(), user::setAboutMePublic, "aboutMePublic", recorder);
        text(change.aboutMeInternal(), user.getAboutMeInternal(), user::setAboutMeInternal, "aboutMeInternal",
                recorder);
        text(change.positionDescription(), user.getPositionDescription(), user::setPositionDescription,
                "positionDescription", recorder);
        if (change.newsletterOptIn() != null && change.newsletterOptIn() != user.isNewsletterOptIn()) {
            recorder.record("newsletterOptIn", user.isNewsletterOptIn(), change.newsletterOptIn(), false, null);
            user.setNewsletterOptIn(change.newsletterOptIn());
        }
        if (change.homeRegionId() != null && !change.homeRegionId().equals(user.getHomeRegionId())) {
            recorder.record("homeRegionId", user.getHomeRegionId(), change.homeRegionId(), false,
                    own ? UserAction.HOME_REGION_CHANGED : null);
            user.setHomeRegionId(change.homeRegionId());
        }
    }

    private static String phone(String field, String value, List<ApiFieldError> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!PhoneNumberValidator.isValid(value)) {
            errors.add(new ApiFieldError(field, "PhoneNumber", "not a valid phone number"));
            return null;
        }
        return PhoneNumberValidator.normalize(value);
    }

    private static void text(String requested, String current, Consumer<String> setter, String field,
                             Recorder recorder) {
        if (requested == null) {
            return;
        }
        String value = requested.isBlank() ? null : requested.strip();
        if (!Objects.equals(value, current)) {
            recorder.record(field, current, value, false, null);
            setter.accept(value);
        }
    }

    private static <T> void value(T requested, T current, Consumer<T> setter, String field, boolean redact,
                                  Recorder recorder) {
        if (requested != null && !requested.equals(current)) {
            recorder.record(field, current, requested, redact, null);
            setter.accept(requested);
        }
    }

    /** User whose private Telegram chat is {@code chatId} (A28). */
    @Transactional(readOnly = true)
    public Optional<User> findByTelegramChatId(String chatId) {
        return repository.findByTelegramChatId(chatId);
    }

    /**
     * Links ({@code chatId}) or unlinks (null) the private Telegram chat; a chat linked to another user is moved.
     * The notification module records TELEGRAM_LINKED / TELEGRAM_UNLINKED.
     */
    @Transactional
    @NoHistory(reason = "the caller records NotificationAction.TELEGRAM_LINKED / TELEGRAM_UNLINKED")
    public User setTelegramChatId(UUID userId, String chatId) {
        if (chatId != null) {
            repository.findByTelegramChatId(chatId).filter(other -> !other.getId().equals(userId)).ifPresent(other -> {
                other.setTelegramChatId(null);
                repository.saveAndFlush(other);
            });
        }
        User user = require(userId);
        user.setTelegramChatId(chatId);
        return user;
    }
}
