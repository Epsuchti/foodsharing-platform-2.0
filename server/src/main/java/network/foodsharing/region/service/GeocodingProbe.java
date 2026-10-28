package network.foodsharing.region.service;

import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** A26: the GEOCODING probe ("8001 Zürich" resolves) for the live Photon provider. */
@Component
@ConditionalOnProperty(name = "app.geocoding.provider", havingValue = "photon", matchIfMissing = true)
public class GeocodingProbe {

    public GeocodingProbe(GeocodingClient client, IntegrationRegistry integrations) {
        integrations.registerProbe(Integration.GEOCODING, () -> client.geocodePostcode("CH", "8001").isPresent());
    }
}
