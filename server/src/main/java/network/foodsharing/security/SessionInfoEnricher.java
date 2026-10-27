package network.foodsharing.security;

/**
 * Adds module data to {@code GET /api/auth/session} (docs/conventions.md A11): the region module adds units and the
 * home region, the legal module pending consents, the onboarding module the journey state. Every bean is applied in
 * {@code @Order}; an enricher must be cheap (one or two queries) and must not fail the session bootstrap.
 */
public interface SessionInfoEnricher {

    void enrich(SessionPrincipal principal, SessionInfoBuilder builder);
}
