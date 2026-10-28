package network.foodsharing.quiz.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * SPI (B9): end of a member's hygiene certificate as recorded by the achievement module (award
 * {@code HYGIENE_CERTIFICATE}). Owned by quiz because achievement depends on quiz; when no implementation answers,
 * the hygiene expiry job falls back to the last passed hygiene quiz.
 */
public interface HygieneCertificates {

    /** Last day of validity of the active certificate; empty = no active certificate award. */
    Optional<LocalDate> validUntil(UUID userId);
}
