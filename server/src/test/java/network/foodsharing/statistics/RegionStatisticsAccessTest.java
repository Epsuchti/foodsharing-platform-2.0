package network.foodsharing.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.statistics.repository.StatisticsReadModel;
import network.foodsharing.statistics.service.RegionStatisticsQueryService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** R3/R13: region statistics for members and administrators only; 1–36 months (was StatisticsFlowIT). */
class RegionStatisticsAccessTest {

    static final UUID REGION = UUID.randomUUID();

    RegionService regions = mock(RegionService.class);
    UserService users = mock(UserService.class);
    StatisticsReadModel readModel = mock(StatisticsReadModel.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    PermissionService permissions = mock(PermissionService.class);
    RegionStatisticsQueryService service = new RegionStatisticsQueryService(regions, users, readModel, actors,
            permissions, Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));
    SessionPrincipal me = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSHARER, "n@x");

    @BeforeEach
    void setUp() {
        when(actors.requirePrincipal()).thenReturn(me);
        when(regions.zoneOf(REGION)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(regions.subtreeIds(REGION)).thenReturn(List.of(REGION));
    }

    @Test
    void outsidersWhoDoNotAdministrateTheRegionAreRefused() {
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateRegion(me, REGION);
        assertThatThrownBy(() -> service.genders(REGION, false)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.ageBands(REGION, true)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.monthlyPickups(REGION, 6)).isInstanceOf(ApiException.class);
        verify(users, never()).findActive(any(java.util.Collection.class));
        verify(readModel, never()).pickupsInRegions(any(), any(), any());
    }

    @Test
    void membersSeeTheStatisticsWithoutBeingAdministrators() {
        when(permissions.isMemberOf(me, REGION)).thenReturn(true);
        assertThat(service.ageBands(REGION, false)).hasSize(9).first()
                .extracting(RegionStatisticsQueryService.Count::label).isEqualTo("18-25");
        assertThat(service.monthlyPickups(REGION, 6)).hasSize(6);
        verify(permissions, never()).requireMayAdministrateRegion(any(), any());
    }

    @Test
    void monthsOutsideOneToThirtySixAreInvalid() {
        when(permissions.isMemberOf(me, REGION)).thenReturn(true);
        for (int months : new int[] {0, 37, 40}) {
            assertThatThrownBy(() -> service.monthlyPickups(REGION, months)).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        }
        assertThat(service.monthlyPickups(REGION, 36)).hasSize(36);
    }
}
