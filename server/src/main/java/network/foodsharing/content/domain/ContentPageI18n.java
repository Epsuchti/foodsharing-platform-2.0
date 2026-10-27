package network.foodsharing.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Title and markdown body of a content page in one language. */
@Getter
@Setter
@Entity
@Table(name = "content_page_i18n")
public class ContentPageI18n extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, length = 32600)
    private String body;
}
