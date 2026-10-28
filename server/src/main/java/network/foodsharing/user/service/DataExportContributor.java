package network.foodsharing.user.service;

import java.util.UUID;

/** One section of the GDPR data export (A21, A40), serialised as JSON. Default: no beans. */
public interface DataExportContributor {

    String section();

    Object export(UUID userId);
}
