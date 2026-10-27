package network.foodsharing.content.domain;

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

/** A user accepted a version of a legal document. */
@Getter
@Setter
@Entity
@Table(name = "legal_acceptance")
public class LegalAcceptance extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 40, updatable = false)
    private LegalDocumentKind kind;

    @Column(name = "document_version", nullable = false, length = 32, updatable = false)
    private String documentVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;
}
