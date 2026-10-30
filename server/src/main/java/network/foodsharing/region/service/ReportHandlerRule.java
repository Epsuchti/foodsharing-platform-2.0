package network.foodsharing.region.service;

import java.util.UUID;
import network.foodsharing.security.SessionPrincipal;

/**
 * Whether the actor handles the reports of a region (A51: REPORT group admins, else ambassadors, plus ARBITRATION
 * admins, handlers of an ancestor region, ORGA) for {@code RegionDetail.mayHandleReports}; implemented by the report
 * module so that {@code region} needs no report dependency (pattern A22). No bean = only ORGA.
 */
public interface ReportHandlerRule {

    boolean mayHandleReports(SessionPrincipal actor, UUID regionId);
}
