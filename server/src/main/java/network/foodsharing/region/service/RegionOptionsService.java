package network.foodsharing.region.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.domain.RegionAction;
import network.foodsharing.region.domain.RegionOption;
import network.foodsharing.region.domain.RegionOptionKey;
import network.foodsharing.region.domain.RegionPin;
import network.foodsharing.region.domain.RegionPinStatus;
import network.foodsharing.region.repository.RegionOptionRepository;
import network.foodsharing.region.repository.RegionPinRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.security.WorkingGroupFunction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region options (plan §13.2 R5) and the region pin (R6). Each option has its own permission, as upstream
 * {@code RegionPermissions}: report and mediation buttons and the report reasons → REPORT function (else
 * ambassadors); the region pickup rule → STORES_COORDINATION function; the user-related options (hiding in the
 * forum, address change notification) → ambassadors of the region; the pin → PR function. ORGA may edit everything.
 */
@Service
public class RegionOptionsService {

    public record PickupRule(boolean active, int timespanDays, int limitNumber, int limitDayNumber, int inactiveHours) {
    }

    public record Options(boolean enableReportButton, boolean enableMediationButton, PickupRule pickupRule,
                          boolean allowHidingInForum, int reportReasonOptions, boolean reportReasonOther,
                          boolean notifyAddressChange) {
    }

    /** Requested changes; null = unchanged. */
    public record OptionsChange(Boolean enableReportButton, Boolean enableMediationButton, PickupRule pickupRule,
                                Boolean allowHidingInForum, Integer reportReasonOptions, Boolean reportReasonOther,
                                Boolean notifyAddressChange, String reason) {
    }

    public record OptionPermissions(boolean mayEditReportButtons, boolean mayEditPickupRule,
                                    boolean mayEditUserRelated, boolean mayEditPin) {
    }

    public record Pin(double latitude, double longitude, String description, boolean active) {
    }

    private static final Map<RegionOptionKey, String> DEFAULTS = new EnumMap<>(Map.ofEntries(
            Map.entry(RegionOptionKey.ENABLE_REPORT_BUTTON, "false"),
            Map.entry(RegionOptionKey.ENABLE_MEDIATION_BUTTON, "false"),
            Map.entry(RegionOptionKey.REGION_PICKUP_RULE_ACTIVE, "false"),
            Map.entry(RegionOptionKey.REGION_PICKUP_RULE_TIMESPAN_DAYS, "0"),
            Map.entry(RegionOptionKey.REGION_PICKUP_RULE_LIMIT_NUMBER, "0"),
            Map.entry(RegionOptionKey.REGION_PICKUP_RULE_LIMIT_DAY_NUMBER, "0"),
            Map.entry(RegionOptionKey.REGION_PICKUP_RULE_INACTIVE_HOURS, "0"),
            Map.entry(RegionOptionKey.ALLOW_HIDING_IN_FORUM, "false"),
            Map.entry(RegionOptionKey.REPORT_REASON_OPTIONS, "1"),
            Map.entry(RegionOptionKey.REPORT_REASON_OTHER, "false"),
            Map.entry(RegionOptionKey.NOTIFY_ADDRESS_CHANGE, "false")));

    private final RegionService regionService;
    private final RegionOptionRepository options;
    private final RegionPinRepository pins;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;

    public RegionOptionsService(RegionService regionService, RegionOptionRepository options, RegionPinRepository pins,
                                CurrentActorService currentActor, PermissionService permissions,
                                HistoryService history) {
        this.regionService = regionService;
        this.options = options;
        this.pins = pins;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 unless member or administrator */
    @Transactional(readOnly = true)
    public Options options(UUID regionId) {
        regionService.require(regionId);
        SessionPrincipal principal = currentActor.requirePrincipal();
        if (!permissions.isMemberOf(principal, regionId)) {
            permissions.requireMayAdministrateRegion(principal, regionId);
        }
        return read(regionId);
    }

    /** Options without permission check (other modules, e.g. the pickup rule in M3). */
    @Transactional(readOnly = true)
    public Options read(UUID regionId) {
        Map<RegionOptionKey, String> values = new EnumMap<>(DEFAULTS);
        options.findByRegionId(regionId).forEach(option -> values.put(option.getOptionKey(), option.getOptionValue()));
        return new Options(bool(values, RegionOptionKey.ENABLE_REPORT_BUTTON),
                bool(values, RegionOptionKey.ENABLE_MEDIATION_BUTTON),
                new PickupRule(bool(values, RegionOptionKey.REGION_PICKUP_RULE_ACTIVE),
                        integer(values, RegionOptionKey.REGION_PICKUP_RULE_TIMESPAN_DAYS),
                        integer(values, RegionOptionKey.REGION_PICKUP_RULE_LIMIT_NUMBER),
                        integer(values, RegionOptionKey.REGION_PICKUP_RULE_LIMIT_DAY_NUMBER),
                        integer(values, RegionOptionKey.REGION_PICKUP_RULE_INACTIVE_HOURS)),
                bool(values, RegionOptionKey.ALLOW_HIDING_IN_FORUM),
                integer(values, RegionOptionKey.REPORT_REASON_OPTIONS),
                bool(values, RegionOptionKey.REPORT_REASON_OTHER),
                bool(values, RegionOptionKey.NOTIFY_ADDRESS_CHANGE));
    }

    @Transactional(readOnly = true)
    public OptionPermissions optionPermissions(UUID regionId) {
        regionService.require(regionId);
        return permissionsOf(currentActor.requirePrincipal(), regionId);
    }

    public OptionPermissions permissionsOf(SessionPrincipal actor, UUID regionId) {
        return new OptionPermissions(permissions.hasFunction(actor, WorkingGroupFunction.REPORT, regionId),
                permissions.hasFunction(actor, WorkingGroupFunction.STORES_COORDINATION, regionId),
                permissions.isAmbassadorOrOrga(actor, regionId),
                permissions.hasFunction(actor, WorkingGroupFunction.PR, regionId));
    }

    /**
     * Changes options; every changed option records OPTION_CHANGED (field = option key, old, new). All or nothing:
     * one option the actor may not edit refuses the whole request.
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403 OPTION_NOT_EDITABLE (args.option = the request field)
     */
    @Transactional
    public Options update(UUID regionId, OptionsChange change) {
        regionService.require(regionId);
        OptionPermissions may = permissionsOf(currentActor.requirePrincipal(), regionId);
        check(change.enableReportButton(), may.mayEditReportButtons(), "enableReportButton");
        check(change.enableMediationButton(), may.mayEditReportButtons(), "enableMediationButton");
        check(change.reportReasonOptions(), may.mayEditReportButtons(), "reportReasonOptions");
        check(change.reportReasonOther(), may.mayEditReportButtons(), "reportReasonOther");
        check(change.pickupRule(), may.mayEditPickupRule(), "pickupRule");
        check(change.allowHidingInForum(), may.mayEditUserRelated(), "allowHidingInForum");
        check(change.notifyAddressChange(), may.mayEditUserRelated(), "notifyAddressChange");

        set(regionId, RegionOptionKey.ENABLE_REPORT_BUTTON, change.enableReportButton(), change.reason());
        set(regionId, RegionOptionKey.ENABLE_MEDIATION_BUTTON, change.enableMediationButton(), change.reason());
        set(regionId, RegionOptionKey.REPORT_REASON_OPTIONS, change.reportReasonOptions(), change.reason());
        set(regionId, RegionOptionKey.REPORT_REASON_OTHER, change.reportReasonOther(), change.reason());
        set(regionId, RegionOptionKey.ALLOW_HIDING_IN_FORUM, change.allowHidingInForum(), change.reason());
        set(regionId, RegionOptionKey.NOTIFY_ADDRESS_CHANGE, change.notifyAddressChange(), change.reason());
        PickupRule rule = change.pickupRule();
        if (rule != null) {
            set(regionId, RegionOptionKey.REGION_PICKUP_RULE_ACTIVE, rule.active(), change.reason());
            set(regionId, RegionOptionKey.REGION_PICKUP_RULE_TIMESPAN_DAYS, rule.timespanDays(), change.reason());
            set(regionId, RegionOptionKey.REGION_PICKUP_RULE_LIMIT_NUMBER, rule.limitNumber(), change.reason());
            set(regionId, RegionOptionKey.REGION_PICKUP_RULE_LIMIT_DAY_NUMBER, rule.limitDayNumber(), change.reason());
            set(regionId, RegionOptionKey.REGION_PICKUP_RULE_INACTIVE_HOURS, rule.inactiveHours(), change.reason());
        }
        return read(regionId);
    }

    /** @throws ApiException 404 REGION_NOT_FOUND or RESOURCE_NOT_FOUND (no pin) */
    @Transactional(readOnly = true)
    public Pin pin(UUID regionId) {
        regionService.require(regionId);
        currentActor.requirePrincipal();
        return pins.findByRegionId(regionId).map(RegionOptionsService::toPin)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Active pin of a region, if any (public region page). */
    @Transactional(readOnly = true)
    public java.util.Optional<Pin> activePin(UUID regionId) {
        return pins.findByRegionId(regionId).filter(pin -> pin.getStatus() == RegionPinStatus.ACTIVE)
                .map(RegionOptionsService::toPin);
    }

    /** Sets the pin (PR function, else ambassadors). @throws ApiException 403, 404 REGION_NOT_FOUND */
    @Transactional
    public Pin updatePin(UUID regionId, Pin pin) {
        Region region = regionService.require(regionId);
        permissions.requireFunction(currentActor.requirePrincipal(), WorkingGroupFunction.PR, regionId);
        RegionPin row = pins.findByRegionId(regionId).orElseGet(() -> {
            RegionPin created = new RegionPin();
            created.setRegionId(regionId);
            return created;
        });
        Pin before = row.getId() == null ? null : toPin(row);
        row.setLatitude(pin.latitude());
        row.setLongitude(pin.longitude());
        row.setDescription(pin.description());
        row.setStatus(pin.active() ? RegionPinStatus.ACTIVE : RegionPinStatus.INACTIVE);
        pins.save(row);
        history.record(history.event(RegionAction.PIN_CHANGED, region.getId()).region(region.getId())
                .change("pin", before == null ? null : describe(before), describe(pin)).build());
        return toPin(row);
    }

    private void set(UUID regionId, RegionOptionKey key, Object value, String reason) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        RegionOption option = options.findByRegionIdAndOptionKey(regionId, key).orElse(null);
        String old = option == null ? DEFAULTS.get(key) : option.getOptionValue();
        if (Objects.equals(old, text)) {
            return;
        }
        if (option == null) {
            option = new RegionOption();
            option.setRegionId(regionId);
            option.setOptionKey(key);
        }
        option.setOptionValue(text);
        options.save(option);
        history.record(history.event(RegionAction.OPTION_CHANGED, regionId).region(regionId)
                .change(key.name(), old, text).reason(reason).build());
    }

    private static void check(Object requested, boolean allowed, String option) {
        if (requested != null && !allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, ApiErrorCode.OPTION_NOT_EDITABLE).withArg("option", option);
        }
    }

    private static boolean bool(Map<RegionOptionKey, String> values, RegionOptionKey key) {
        return Boolean.parseBoolean(values.get(key));
    }

    private static int integer(Map<RegionOptionKey, String> values, RegionOptionKey key) {
        try {
            return Integer.parseInt(values.get(key));
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    private static Pin toPin(RegionPin row) {
        return new Pin(row.getLatitude(), row.getLongitude(), row.getDescription(),
                row.getStatus() == RegionPinStatus.ACTIVE);
    }

    private static String describe(Pin pin) {
        return pin.latitude() + "," + pin.longitude() + (pin.active() ? " active" : " inactive");
    }
}
