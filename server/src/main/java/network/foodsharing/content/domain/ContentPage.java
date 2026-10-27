package network.foodsharing.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A content page or learning chapter; texts in {@link ContentPageI18n}. */
@Getter
@Setter
@Entity
@Table(name = "content_page")
public class ContentPage extends BaseEntity {

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private ContentPageKind kind;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "published", nullable = false)
    private boolean published;
}
