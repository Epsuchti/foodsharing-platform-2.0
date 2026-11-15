package network.foodsharing.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.region.domain.Region;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.junit.jupiter.api.Test;

/**
 * R12 conference link (rules the old e2e spec asserted, decision 180): only members of the region or group (and ORGA,
 * {@link PermissionService#requireMember}) get it; the unit's own link wins, else the configured template with the slug.
 * That the settings accept only https links is the settings form's rule (regions-pages.spec, settings tab).
 */
class ConferenceLinkServiceTest {

    private final RegionService regions = mock(RegionService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final ConferenceLinkService service = new ConferenceLinkService(regions, currentActor, permissions,
            new AppProperties());

    private final Region zurich = new Region();
    private final SessionPrincipal outsider = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSAVER,
            "profile@local");

    ConferenceLinkServiceTest() {
        zurich.setId(UUID.randomUUID());
        zurich.setSlug("zuerich-stadt");
        when(regions.require(zurich.getId())).thenReturn(zurich);
        when(currentActor.requirePrincipal()).thenReturn(outsider);
    }

    @Test
    void onlyMembersGetTheLink() {
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions).requireMember(outsider, zurich.getId());
        assertThatThrownBy(() -> service.conferenceUrl(zurich.getId())).isInstanceOf(ApiException.class);
    }

    @Test
    void theUnitsOwnLinkWinsElseTheTemplateWithTheSlug() {
        assertThat(service.conferenceUrl(zurich.getId())).isEqualTo("https://meet.jit.si/foodsharing-zuerich-stadt");
        zurich.setConferenceUrl("  ");
        assertThat(service.linkOf(zurich)).isEqualTo("https://meet.jit.si/foodsharing-zuerich-stadt");
        zurich.setConferenceUrl("https://meet.jit.si/foodsharing-zuerich-neu");
        assertThat(service.conferenceUrl(zurich.getId())).isEqualTo("https://meet.jit.si/foodsharing-zuerich-neu");
    }
}
