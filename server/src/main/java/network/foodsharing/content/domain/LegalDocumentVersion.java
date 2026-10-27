package network.foodsharing.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A published version of a legal document. */
@Getter
@Setter
@Entity
@Table(name = "legal_document_version")
public class LegalDocumentVersion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40, updatable = false)
    private LegalDocumentKind kind;

    @Column(name = "document_version", nullable = false, length = 32, updatable = false)
    private String documentVersion;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "require_reconsent", nullable = false)
    private boolean requireReconsent;
}
