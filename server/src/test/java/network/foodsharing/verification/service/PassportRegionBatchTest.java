package network.foodsharing.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import network.foodsharing.verification.domain.PassportAction;
import network.foodsharing.verification.domain.PassportIssue;
import network.foodsharing.verification.domain.PassportRequest;
import network.foodsharing.verification.repository.PassportIssueRepository;
import network.foodsharing.verification.repository.PassportRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Ve3 passport requests and the ambassadors' batch (formerly asserted by VerificationApiIT): one open request at a
 * time; the batch is for the region's ambassadors (or ORGA) and the region's members only; a renewal starts today
 * and runs three years, fulfils the open requests in the ambassador's name, is logged per person and, when asked,
 * rings the people (PASSPORT_CREATED after commit).
 */
class PassportRegionBatchTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final UUID REGION = UUID.fromString("00000010-0001-4000-8000-000000000040");

    private final PassportIssueRepository issues = mock(PassportIssueRepository.class);
    private final PassportRequestRepository requests = mock(PassportRequestRepository.class);
    private final PassportPdfRenderer renderer = mock(PassportPdfRenderer.class);
    private final UserService users = mock(UserService.class);
    private final RegionService regions = mock(RegionService.class);
    private final MembershipService memberships = mock(MembershipService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    private final List<Object> published = new ArrayList<>();
    private final SessionPrincipal bot = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR,
            "bot@local");
    private PassportService passports;
    private User sleepy;
    private User sarah;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        passports = new PassportService(issues, requests, renderer, new WalletConfiguration(properties), users, regions,
                memberships, currentActor, permissions, history, mock(ProgressTracker.class), published::add,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
        Region region = new Region();
        region.setId(REGION);
        region.setSlug("zuerich-stadt");
        when(regions.require(REGION)).thenReturn(region);
        when(regions.zoneOf(REGION)).thenReturn(ZoneId.of("Europe/Zurich"));
        when(currentActor.requirePrincipal()).thenReturn(bot);
        sleepy = member("Marco");
        sarah = member("Sarah");
        when(memberships.membersOf(REGION)).thenReturn(List.of(sleepy.getId(), sarah.getId()));
        when(users.findAll(any())).thenReturn(List.of(sleepy, sarah));
        when(renderer.render(any(), any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
    }

    private static User member(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayName(name);
        user.setVerified(true);
        user.setHomeRegionId(REGION);
        user.setPreferredLanguage(Language.DE);
        return user;
    }

    private static PassportService.BatchInput renew(List<UUID> ids, boolean notify) {
        return new PassportService.BatchInput(ids, true, true, null, null, notify);
    }

    private static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e.getCode();
        }
        return null;
    }

    @Test
    void oneOpenRequestAtATime() {
        when(users.require(sarah.getId())).thenReturn(sarah);
        when(currentActor.requireUserId()).thenReturn(sarah.getId());
        when(requests.existsByUserIdAndFulfilledAtIsNull(sarah.getId())).thenReturn(true);
        assertThat(code(passports::request)).isEqualTo(ApiErrorCode.PASSPORT_REQUEST_PENDING);
        verify(requests, never()).save(any());
    }

    @Test
    void theBatchIsForTheRegionsAmbassadorsAndItsMembersOnly() {
        UUID outsider = UUID.randomUUID();
        assertThat(code(() -> passports.createForRegion(REGION, renew(List.of(outsider), false))))
                .isEqualTo(ApiErrorCode.USERS_NOT_IN_REGION);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireAmbassadorOrOrga(bot, REGION);
        assertThat(code(() -> passports.createForRegion(REGION, renew(List.of(sleepy.getId()), false))))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verify(issues, never()).save(any());
    }

    @Test
    void aRenewalRunsThreeYearsFromTodayFulfilsOpenRequestsAndRingsWhenAsked() {
        PassportRequest open = new PassportRequest();
        open.setUserId(sarah.getId());
        when(requests.findByUserIdAndFulfilledAtIsNull(sarah.getId())).thenReturn(List.of(open));

        passports.createForRegion(REGION, renew(List.of(sleepy.getId(), sarah.getId()), true));

        ArgumentCaptor<PassportIssue> issued = ArgumentCaptor.forClass(PassportIssue.class);
        verify(issues, times(2)).save(issued.capture());
        assertThat(issued.getAllValues()).allSatisfy(issue -> {
            assertThat(issue.getValidFrom()).isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(issue.getValidUntil()).isEqualTo(LocalDate.of(2029, 11, 2));
            assertThat(issue.getIssuedBy()).isEqualTo(bot.userId());
        });
        verify(users).setLastPassportAt(eq(sleepy.getId()), any());
        assertThat(open.getFulfilledAt()).isEqualTo(NOW);
        assertThat(open.getFulfilledBy()).isEqualTo(bot.userId());
        verify(history).event(PassportAction.PASSPORT_GENERATED_BY_AMBASSADOR, sleepy.getId());
        verify(history).event(PassportAction.PASSPORT_GENERATED_BY_AMBASSADOR, sarah.getId());
        assertThat(published).containsExactly(new PassportService.PassportsCreated(
                List.of(sleepy.getId(), sarah.getId()), REGION));
    }

    @Test
    void withoutNotifyNobodyIsRung() {
        passports.createForRegion(REGION, renew(List.of(sleepy.getId()), false));
        assertThat(published).isEmpty();
    }
}
