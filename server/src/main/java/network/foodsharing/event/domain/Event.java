package network.foodsharing.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** An event of a region or working group (upstream fs_event). */
@Getter
@Setter
@Entity
@Table(name = "event")
public class Event extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private EventType type;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 32600)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "is_public", nullable = false)
    private boolean publicEvent;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "online_link", length = 500)
    private String onlineLink;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
