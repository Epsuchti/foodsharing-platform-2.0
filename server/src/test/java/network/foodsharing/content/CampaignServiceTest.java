package network.foodsharing.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.admin.service.FeatureToggleService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.content.service.CampaignService;
import network.foodsharing.content.service.ContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** C8: the petition banner answers 503 PETITION_INACTIVE while no petition runs. */
class CampaignServiceTest {

    ConfigurationService configuration = mock(ConfigurationService.class);
    FeatureToggleService toggles = mock(FeatureToggleService.class);
    CampaignService service = new CampaignService(configuration, toggles, mock(ContentService.class));

    @BeforeEach
    void setUp() {
        when(configuration.url(ConfigurationKey.PETITION_URL)).thenReturn("https://petition.example/foodsharing");
        when(configuration.integer(ConfigurationKey.PETITION_SIGNATURES)).thenReturn(4213);
        when(configuration.integer(ConfigurationKey.PETITION_DAYS_LEFT)).thenReturn(21);
    }

    private static ApiException failure(Runnable call) {
        return catchThrowableOfType(ApiException.class, call::run);
    }

    @Test
    void aConfiguredRunningPetitionIsReturned() {
        var petition = service.petition();
        assertThat(petition.href()).isEqualTo("https://petition.example/foodsharing");
        assertThat(petition.signatures()).isEqualTo(4213);
        assertThat(petition.daysLeft()).isEqualTo(21);
    }

    @Test
    void withoutUrlOrAfterTheLastDayThePetitionIsInactive() {
        when(configuration.url(ConfigurationKey.PETITION_URL)).thenReturn("");
        ApiException noUrl = failure(service::petition);
        assertThat(noUrl.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(noUrl.getCode()).isEqualTo(ApiErrorCode.PETITION_INACTIVE);

        when(configuration.url(ConfigurationKey.PETITION_URL)).thenReturn("https://petition.example/foodsharing");
        when(configuration.integer(ConfigurationKey.PETITION_DAYS_LEFT)).thenReturn(0);
        assertThat(failure(service::petition).getCode()).isEqualTo(ApiErrorCode.PETITION_INACTIVE);
        when(configuration.integer(ConfigurationKey.PETITION_DAYS_LEFT)).thenReturn(-3);
        assertThat(failure(service::petition).getCode()).isEqualTo(ApiErrorCode.PETITION_INACTIVE);
    }

    @Test
    void aSwitchedOffToggleStaysFeatureDisabled() {
        doThrow(ApiException.notFound(ApiErrorCode.FEATURE_DISABLED)).when(toggles)
                .requireEnabled(FeatureToggle.PETITION_BANNER);
        when(configuration.url(ConfigurationKey.PETITION_URL)).thenReturn("");
        assertThat(failure(service::petition).getCode()).isEqualTo(ApiErrorCode.FEATURE_DISABLED);
    }
}
