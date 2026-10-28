package network.foodsharing.automation.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.domain.RequestPolicy;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.repository.RequestPolicyRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Region automation overview and public holidays of a region. */
@Service
public class AutomationOverviewService {

    public record StoreOverview(Store store, PickupPolicy pickupPolicy, RequestPolicy requestPolicy) {
    }

    private final PickupPolicyRepository pickupPolicies;
    private final RequestPolicyRepository requestPolicies;
    private final StoreService stores;
    private final RegionService regions;
    private final CleaningExemptionService exemptions;
    private final HolidayCalendarService holidays;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;

    public AutomationOverviewService(PickupPolicyRepository pickupPolicies, RequestPolicyRepository requestPolicies,
                                     StoreService stores, RegionService regions, CleaningExemptionService exemptions,
                                     HolidayCalendarService holidays, PermissionService permissions,
                                     CurrentActorService currentActor) {
        this.pickupPolicies = pickupPolicies;
        this.requestPolicies = requestPolicies;
        this.stores = stores;
        this.regions = regions;
        this.exemptions = exemptions;
        this.holidays = holidays;
        this.permissions = permissions;
        this.currentActor = currentActor;
    }

    /** Live stores of the region subtree with their policies, by name. @throws ApiException 403, 404 */
    @Transactional(readOnly = true)
    public List<StoreOverview> region(UUID regionId) {
        exemptions.requireRegionAutomation(regionId);
        List<Store> list = stores.liveStoresInRegions(regions.subtreeIds(regionId));
        List<UUID> ids = list.stream().map(Store::getId).toList();
        Map<UUID, PickupPolicy> pickup = pickupPolicies.findByStoreIdIn(ids).stream()
                .collect(Collectors.toMap(PickupPolicy::getStoreId, Function.identity()));
        Map<UUID, RequestPolicy> request = requestPolicies.findByStoreIdIn(ids).stream()
                .collect(Collectors.toMap(RequestPolicy::getStoreId, Function.identity()));
        return list.stream().sorted(java.util.Comparator.comparing(Store::getName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new StoreOverview(s, pickup.get(s.getId()), request.get(s.getId()))).toList();
    }

    /**
     * Holidays of the year at the region's country and canton (region members).
     *
     * @throws ApiException 403, 404 REGION_NOT_FOUND, 400 VALIDATION_FAILED (year), 503 INTEGRATION_UNAVAILABLE
     */
    @Transactional(readOnly = true)
    public List<HolidayCalendarService.Holiday> holidays(UUID regionId, int year) {
        regions.require(regionId);
        var actor = currentActor.requirePrincipal();
        if (!permissions.isMemberOf(actor, regionId) && !permissions.mayAdministrateRegion(actor, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (year < 2000 || year > 2100) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "year");
        }
        try {
            return holidays.holidays(year, holidays.placeOf(regionId));
        } catch (PickupPolicyEvaluator.HolidayCalendarUnavailableException ex) {
            throw network.foodsharing.common.integration.IntegrationRegistry.unavailable(
                    network.foodsharing.common.integration.Integration.HOLIDAY_CALENDAR);
        }
    }
}
