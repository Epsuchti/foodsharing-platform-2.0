package network.foodsharing.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * C14 / C7: the donation settings are the typed view on the {@code donation.*} configuration keys: saving writes every
 * key (hours as numbers, the campaign pages comma separated, missing URLs empty), records DONATION_SETTINGS_CHANGED and
 * reloads the allowed frame sources; only ORGA reads or saves them.
 */
class DonationSettingsServiceTest {

    private final ConfigurationService configuration = mock(ConfigurationService.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final DonationFrameSources frameSources = mock(DonationFrameSources.class);
    private final DonationSettingsService donations =
            new DonationSettingsService(configuration, actors, history, frameSources);

    @BeforeEach
    void setUp() {
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void savingWritesTheDonationKeys() {
        donations.update(new DonationSettingsService.Donation(false, true, 168, 48, null,
                "https://spenden.example/einmalig", null, "https://spenden.example/freundeskreis", null,
                List.of("donation-campaign-1", "donation-campaign-2")));

        verify(actors).requireRole(Role.ORGA);
        verify(configuration).set(ConfigurationKey.DONATION_SHOW_CAMPAIGN_CARD, "false");
        verify(configuration).set(ConfigurationKey.DONATION_SHOW_MODAL, "true");
        verify(configuration).set(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_IN, "168");
        verify(configuration).set(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_OUT, "48");
        verify(configuration).set(ConfigurationKey.DONATION_INFO_URL, "");
        verify(configuration).set(ConfigurationKey.DONATION_IFRAME_ONE_TIME_URL, "https://spenden.example/einmalig");
        verify(configuration).set(ConfigurationKey.DONATION_IFRAME_CAMPAIGN_URL, "");
        verify(configuration).set(ConfigurationKey.DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL,
                "https://spenden.example/freundeskreis");
        verify(configuration).set(ConfigurationKey.DONATION_CAMPAIGN_PAGE_SLUGS, "donation-campaign-1,donation-campaign-2");
        assertThat(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_OUT.key()).isEqualTo("donation.modal-hours-logged-out");
        ArgumentCaptor<HistoryEvent> event = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(history).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AdminAction.DONATION_SETTINGS_CHANGED);
        verify(frameSources).invalidate();
    }

    @Test
    void onlyOrgaReadsAndSaves() {
        when(actors.requireRole(Role.ORGA)).thenThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED));
        assertThatThrownBy(donations::get).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> donations.update(new DonationSettingsService.Donation(true, false, 1, 1, null, null,
                null, null, null, List.of()))).isInstanceOf(ApiException.class);
        verify(configuration, never()).set(any(), any());
    }
}
