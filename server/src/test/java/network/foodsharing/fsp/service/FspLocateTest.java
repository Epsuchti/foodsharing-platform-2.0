package network.foodsharing.fsp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.Role;
import network.foodsharing.config.AppProperties;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspFollowerRepository;
import network.foodsharing.fsp.repository.FspWallPostRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.GeocodingClient;
import network.foodsharing.region.service.GeocodingClient.GeoPoint;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Decision 171 (D2): a Fairteiler saved without a pin is placed at its full address (postcode fallback in the client). */
class FspLocateTest {

    static final UUID REGION = UUID.randomUUID();

    /** Stops {@code create} right after the position is set. */
    static final class Saved extends RuntimeException {
    }

    private final FoodSharePointRepository fsps = mock(FoodSharePointRepository.class);
    private final FspPermissionService access = mock(FspPermissionService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final GeocodingClient geocoding = mock(GeocodingClient.class);
    private final AtomicReference<FoodSharePoint> saved = new AtomicReference<>();
    private FspService service;

    @BeforeEach
    void setUp() {
        service = new FspService(fsps, mock(FspFollowerRepository.class), mock(FspWallPostRepository.class), access,
                actors, mock(RegionService.class), geocoding, mock(UploadService.class),
                mock(NotificationService.class), mock(MailOutboxService.class), mock(UserService.class),
                mock(HistoryService.class), new AppProperties(), Clock.systemUTC(), mock(FeatureToggleService.class),
                mock(FspCleaningRegionService.class));
        SessionPrincipal actor = new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.AMBASSADOR, "a@x.ch");
        when(actors.requirePrincipal()).thenReturn(actor);
        when(access.mayAdd(actor, REGION)).thenReturn(true);
        when(fsps.save(any())).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            throw new Saved();
        });
    }

    private FspService.FspInput input(Double lat, Double lon) {
        return new FspService.FspInput("Fairteiler Bahnhof", null, "Bahnhofstrasse 1", "8001", "Zürich", lat, lon,
                null, null, false, false, "Europe/Zurich");
    }

    @Test
    void withoutPinTheFullAddressIsGeocoded() {
        when(geocoding.geocodeAddress("CH", "Bahnhofstrasse 1", "8001", "Zürich"))
                .thenReturn(Optional.of(new GeoPoint(47.3769, 8.5402)));

        assertThatThrownBy(() -> service.create(REGION, input(null, null))).isInstanceOf(Saved.class);

        assertThat(saved.get().getLat()).isEqualTo(47.3769);
        assertThat(saved.get().getLon()).isEqualTo(8.5402);
        verify(geocoding, never()).geocodePostcode(anyString(), anyString());
    }

    @Test
    void aPinSetByHandIsKept() {
        assertThatThrownBy(() -> service.create(REGION, input(47.0, 8.0))).isInstanceOf(Saved.class);

        assertThat(saved.get().getLat()).isEqualTo(47.0);
        verify(geocoding, never()).geocodeAddress(any(), any(), any(), any());
    }

    @Test
    void anUnavailableGeocoderNeverBlocksSaving() {
        when(geocoding.geocodeAddress(any(), any(), any(), any()))
                .thenThrow(new GeocodingClient.GeocodingUnavailableException("down", null));

        assertThatThrownBy(() -> service.create(REGION, input(null, null))).isInstanceOf(Saved.class);

        assertThat(saved.get().getLat()).isNull();
    }
}
