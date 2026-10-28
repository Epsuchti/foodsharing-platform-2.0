package network.foodsharing.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A release note (what is new); texts in {@link ReleaseNoteI18n}. */
@Getter
@Setter
@Entity
@Table(name = "release_note")
public class ReleaseNote extends BaseEntity {

    @Column(name = "release_version", nullable = false, length = 40)
    private String releaseVersion;

    @Column(name = "released_at", nullable = false)
    private LocalDate releasedAt;
}
