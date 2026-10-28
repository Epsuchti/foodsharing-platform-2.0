package network.foodsharing.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** The secret of a user's personal calendar feed URL (A11, upstream {@code fs_apitoken}); one per user. */
@Getter
@Setter
@Entity
@Table(name = "calendar_token")
public class CalendarToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;
}
