package network.foodsharing.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Summary and body of a legal version in one language. */
@Getter
@Setter
@Entity
@Table(name = "legal_document_version_i18n")
public class LegalDocumentVersionI18n extends BaseEntity {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @Column(name = "summary", length = 4000)
    private String summary;

    @Column(name = "body", nullable = false, length = 32600)
    private String body;
}
