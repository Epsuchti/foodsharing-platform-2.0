package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.automation.domain.CleaningRuleExemption;
import network.foodsharing.automation.repository.CleaningRuleExemptionRepository;
import network.foodsharing.automation.repository.RequestPolicyRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Region automation settings (AU2, AU13; was PickupPolicyFlowIT): a cleaning exemption is per region and person
 * (409 when it exists, region administrators only), the request policy's distance is 0–200 km.
 */
class RegionAutomationSettingsTest {

    static final UUID REGION = UUID.randomUUID();
    static final UUID USER = UUID.randomUUID();
    static final SessionPrincipal ACTOR = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR,
            "bot@test.local");

    final CleaningRuleExemptionRepository exemptions = mock(CleaningRuleExemptionRepository.class);
    final PermissionService permissions = mock(PermissionService.class);
    final CleaningExemptionService exemptionService;

    RegionAutomationSettingsTest() {
        CurrentActorService actor = mock(CurrentActorService.class);
        when(actor.requirePrincipal()).thenReturn(ACTOR);
        exemptionService = new CleaningExemptionService(exemptions, mock(RegionService.class), mock(UserService.class),
                permissions, actor, mock(HistoryService.class, RETURNS_DEEP_STUBS));
    }

    @Test
    void aSecondExemptionOfThePersonIsAConflict() {
        when(permissions.mayAdministrateRegion(ACTOR, REGION)).thenReturn(true);
        when(exemptions.existsByRegionIdAndUserId(REGION, USER)).thenReturn(true);
        assertThatThrownBy(() -> exemptionService.add(REGION, USER, "Rücken"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ApiErrorCode.CLEANING_EXEMPTION_EXISTS));
        verify(exemptions, never()).save(any());
    }

    @Test
    void aNewExemptionIsSavedWithItsReasonTrimmed() {
        when(permissions.mayAdministrateRegion(ACTOR, REGION)).thenReturn(true);
        when(exemptions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CleaningRuleExemption saved = exemptionService.add(REGION, USER, "  Rücken ");
        assertThat(saved.getRegionId()).isEqualTo(REGION);
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getReason()).isEqualTo("Rücken");
    }

    @Test
    void onlyRegionAdministratorsManageExemptions() {
        assertThatThrownBy(() -> exemptionService.add(REGION, USER, null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(exemptions, never()).save(any());
    }

    @Test
    void theRequestPolicyDistanceIsAtMost200Km() {
        RequestPolicyRepository policies = mock(RequestPolicyRepository.class);
        PickupPolicyService pickupPolicies = mock(PickupPolicyService.class);
        UUID storeId = UUID.randomUUID();
        Store store = new Store();
        store.setId(storeId);
        when(pickupPolicies.requireAutomationStore(storeId)).thenReturn(store);
        when(policies.findByStoreId(storeId)).thenReturn(Optional.empty());
        when(policies.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        RequestPolicyService service = new RequestPolicyService(policies, pickupPolicies,
                mock(HistoryService.class, RETURNS_DEEP_STUBS));
        for (double km : new double[] {500, 200.5, -1, Double.NaN}) {
            assertThatThrownBy(() -> service.put(storeId, new RequestPolicyService.RequestPolicyInput(true, true, km,
                    null))).as("%s km", km).isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getArgs()).containsEntry("field", "maximumDistanceKm");
                    });
        }
        verify(policies, never()).saveAndFlush(any());
        assertThat(service.put(storeId, new RequestPolicyService.RequestPolicyInput(true, true, 200, null))
                .getMaximumDistanceKm()).isEqualTo(200);
    }
}
