package network.foodsharing.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
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
 * A platform user (table {@code user_account}). M1 fields; the remaining §15.1 fields are added by later
 * milestones. Enum columns store the Java constant name (e.g. {@code GWS}, {@code FOODSAVER}).
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
