package network.foodsharing.fsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.domain.RegionCleaningFsp;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.RegionCleaningFspRepository;
import network.foodsharing.fsp.service.FspCleaningRegionService;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.api.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FS2 D7/D8: who edits a region's cleaning Fairteiler, and which Fairteiler can be linked. */
class FspCleaningRegionServiceTest {

    static final UUID REGION = UUID.randomUUID();
    static final SessionPrincipal ACTOR = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.FOODSAVER,
            "a@local");

    RegionCleaningFspRepository links;
    FoodSharePointRepository fsps;
    PermissionService permissions;
    FspCleaningRegionService service;

    @BeforeEach
    void setUp() {
        links = mock(RegionCleaningFspRepository.class);
        fsps = mock(FoodSharePointRepository.class);
        permissions = mock(PermissionService.class);
        CurrentActorService actors = mock(CurrentActorService.class);
        when(actors.requirePrincipal()).thenReturn(ACTOR);
        when(permissions.mayAdministrateRegion(ACTOR, REGION)).thenReturn(true);
        service = new FspCleaningRegionService(links, fsps, mock(RegionService.class), permissions, actors,
                mock(HistoryService.class, RETURNS_DEEP_STUBS));
    }

    static FoodSharePoint fsp(UUID regionId, FspStatus status, boolean cleaning, boolean deleted) {
        FoodSharePoint fsp = new FoodSharePoint();
        fsp.setId(UUID.randomUUID());
        fsp.setName("Fairteiler");
        fsp.setRegionId(regionId);
        fsp.setStatus(status);
        fsp.setCleaningEnabled(cleaning);
        fsp.setDeletedAt(deleted ? Instant.parse("2026-10-01T00:00:00Z") : null);
        return fsp;
    }

    void rejected(FoodSharePoint fsp) {
        when(fsps.findAllById(any())).thenReturn(List.of(fsp));
        assertThatThrownBy(() -> service.replace(REGION, List.of(fsp.getId()))).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ApiErrorCode.FSP_NOT_CLEANING_CANDIDATE);
        verify(links, never()).save(any());
    }

    @Test
    void fairteilerOfAnotherRegionIsRejected() {
        rejected(fsp(UUID.randomUUID(), FspStatus.ACTIVE, true, false));
    }

    @Test
    void inactiveFairteilerIsRejected() {
        rejected(fsp(REGION, FspStatus.INACTIVE, true, false));
    }

    @Test
    void fairteilerWithoutCleaningIsRejected() {
        rejected(fsp(REGION, FspStatus.ACTIVE, false, false));
    }

    @Test
    void deletedFairteilerIsRejected() {
        rejected(fsp(REGION, FspStatus.ACTIVE, true, true));
    }

    @Test
    void unknownFairteilerIsRejected() {
        when(fsps.findAllById(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.replace(REGION, List.of(UUID.randomUUID())))
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ApiErrorCode.FSP_NOT_CLEANING_CANDIDATE);
    }

    @Test
    void candidateIsLinkedAndAStaleLinkRemoved() {
        FoodSharePoint ok = fsp(REGION, FspStatus.ACTIVE, true, false);
        RegionCleaningFsp stale = new RegionCleaningFsp();
        stale.setRegionId(REGION);
        stale.setFspId(UUID.randomUUID());
        when(fsps.findAllById(any())).thenReturn(List.of(ok));
        when(links.findByRegionId(REGION)).thenReturn(List.of(stale));
        service.replace(REGION, List.of(ok.getId()));
        verify(links).delete(stale);
        verify(links).save(org.mockito.ArgumentMatchers.argThat(l -> l.getFspId().equals(ok.getId())
                && l.getRegionId().equals(REGION)));
    }

    @Test
    void automationUsersMayEditOthersMayNot() {
        when(permissions.mayAdministrateRegion(ACTOR, REGION)).thenReturn(false);
        assertThatThrownBy(() -> service.get(REGION)).extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        when(permissions.has(eq(ACTOR), eq(Permission.CAN_USE_AUTOMATIONS), eq(REGION))).thenReturn(true);
        assertThat(service.get(REGION).selected()).isEmpty();
    }

    @Test
    void removeLinksDropsEveryRegionLinkOfTheFairteiler() {
        FoodSharePoint off = fsp(REGION, FspStatus.ACTIVE, false, false);
        RegionCleaningFsp link = new RegionCleaningFsp();
        link.setRegionId(REGION);
        link.setFspId(off.getId());
        when(links.findByFspId(off.getId())).thenReturn(List.of(link));
        assertThat(service.removeLinks(off, "cleaning switched off")).isEqualTo(1);
        verify(links).delete(link);
    }
}
