package network.foodsharing.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.api.model.Theme;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistorySubject;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.Tracked;

/**
 * A platform user (table {@code user_account}). M1 fields plus the §15.1 fields needed by milestone 2 (plan §8.3);
 * the rest (sleep, statistics, options) arrive with their milestones. Enum columns store the Java constant name
 * (e.g. {@code GWS}, {@code FOODSAVER}). The home region is a plain id (FK in the database) so the user module does
 * not depend on the region module.
 */
@Getter
@Setter
@Entity
@Table(name = "user_account")
public class User extends BaseEntity implements HistorySubject {

    @Tracked
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Tracked
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Tracked
    @Column(name = "first_name", length = 100)
    private String firstName;

    @Tracked
    @Column(name = "last_name", length = 100)
    private String lastName;

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 5)
    private Language preferredLanguage = Language.DE;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 10)
    private Theme theme = Theme.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.FOODSHARER;

    @Enumerated(EnumType.STRING)
    @Column(name = "quiz_role", length = 20)
    private Role quizRole;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "foodsaver_number", unique = true)
    private Long foodsaverNumber;

    // --- milestone 2 (plan §15.1) ---

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender = Gender.NOT_SELECTED;

    @Tracked(redact = true)
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Tracked(redact = true)
    @Column(name = "mobile_phone", length = 50)
    private String mobilePhone;

    @Tracked(redact = true)
    @Column(name = "landline_phone", length = 50)
    private String landlinePhone;

    @Column(name = "homepage", length = 500)
    private String homepage;

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_visibility", nullable = false, length = 20)
    private ContactVisibility contactVisibility = ContactVisibility.TEAM;

    @Tracked
    @Column(name = "street", length = 200)
    private String street;

    @Tracked
    @Column(name = "postcode", length = 20)
    private String postcode;

    @Tracked
    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Tracked
    @Column(name = "home_region_id")
    private UUID homeRegionId;

    @Column(name = "about_me_public", length = 16000)
    private String aboutMePublic;

    @Column(name = "about_me_internal", length = 16000)
    private String aboutMeInternal;

    @Column(name = "position_description", length = 500)
    private String positionDescription;

    @Column(name = "newsletter_opt_in", nullable = false)
    private boolean newsletterOptIn;

    @Column(name = "identity_confirmed_at")
    private Instant identityConfirmedAt;

    @Column(name = "identity_confirmed_by")
    private UUID identityConfirmedBy;

    @Column(name = "last_passport_at")
    private Instant lastPassportAt;

    @Column(name = "info_mail_message", nullable = false)
    private boolean infoMailMessage = true;

    /** Reminder bell + mail before own pickups (B5). */
    @Column(name = "pickup_reminders_enabled", nullable = false)
    private boolean pickupRemindersEnabled = true;

    /** Linked private Telegram chat (M4 A28); null = not linked. */
    @Column(name = "telegram_chat_id", length = 40, unique = true)
    private String telegramChatId;

    @Column(name = "no_automatic_delete", nullable = false)
    private boolean noAutomaticDelete;

    @Column(name = "beta_tester", nullable = false)
    private boolean betaTester;

    @Override
    public SubjectType historySubjectType() {
        return SubjectType.USER;
    }

    @Override
    public UUID historySubjectId() {
        return getId();
    }

    @Override
    public HistoryAction historyFieldChangedAction() {
        return UserAction.FIELD_CHANGED;
    }
}
