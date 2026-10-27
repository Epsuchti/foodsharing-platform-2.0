package network.foodsharing.intropickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Settings of a guide (iCal link, personal feed token, phone). */
@Getter
@Setter
@Entity
@Table(name = "guide_profile")
public class GuideProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "ical_link", length = 2000)
    private String icalLink;

    @Column(name = "ical_feed_token", length = 64)
    private String icalFeedToken;

    @Column(name = "phone", length = 50)
    private String phone;
}
