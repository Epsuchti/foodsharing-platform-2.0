package network.foodsharing.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.domain.ConfigurationKey.Type;
import network.foodsharing.admin.domain.ConfigurationSetting;
import network.foodsharing.admin.repository.ConfigurationSettingRepository;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A43/C14: typed getters with defaults, validation per type, history on change only. */
class ConfigurationServiceTest {

    ConfigurationSettingRepository repository = mock(ConfigurationSettingRepository.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    ConfigurationService service = new ConfigurationService(repository, mock(CurrentActorService.class), history);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "INTEGER | 42 | 42",
            "INTEGER | ' 007 ' | 7",
            "INTEGER | 4.2 | ",
            "BOOLEAN | TRUE | true",
            "BOOLEAN | yes | ",
            "EMAIL | support@local.test | support@local.test",
            "EMAIL | nope | ",
            "URL | https://x.org/a | https://x.org/a",
            "URL | '' | ''",
            "URL | ftp://x.org | ",
            "URL | javascript:alert(1) | ",
            "LIST | ' a, ,b ' | 'a,b'",
            "STRING | ' text ' | text"})
    void normalizesPerType(Type type, String raw, String expected) {
        assertThat(ConfigurationService.normalize(type, raw)).isEqualTo(expected);
    }

    @Test
    void gettersFallBackToTheDefault() {
        when(repository.findByConfigKey(any())).thenReturn(Optional.empty());
        assertThat(service.integer(ConfigurationKey.DONATION_MODAL_HOURS_LOGGED_IN)).isEqualTo(168);
        assertThat(service.bool(ConfigurationKey.DONATION_SHOW_MODAL)).isFalse();
        assertThat(service.url(ConfigurationKey.PETITION_URL)).isNull();
        assertThat(service.list(ConfigurationKey.DONATION_CAMPAIGN_PAGE_SLUGS)).isEmpty();
    }

    @Test
    void updateValidatesAndRecordsOnlyChanges() {
        when(repository.findByConfigKey("petition.signatures")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThatThrownBy(() -> service.update("petition.signatures", "many")).isInstanceOfSatisfying(
                ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.CONFIGURATION_VALUE_INVALID));
        assertThatThrownBy(() -> service.update("no.such.key", "1")).isInstanceOfSatisfying(
                ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.CONFIGURATION_KEY_NOT_FOUND));
        assertThat(service.update("petition.signatures", "0").value()).isEqualTo("0");
        verify(history, never()).event(AdminAction.CONFIGURATION_CHANGED, HistoryService.SYSTEM_SUBJECT_ID);
        ConfigurationSetting stored = new ConfigurationSetting();
        stored.setConfigKey("petition.signatures");
        stored.setConfigValue("0");
        when(repository.findByConfigKey("petition.signatures")).thenReturn(Optional.of(stored));
        assertThat(service.update("petition.signatures", "12").value()).isEqualTo("12");
        verify(history).event(AdminAction.CONFIGURATION_CHANGED, HistoryService.SYSTEM_SUBJECT_ID);
    }
}
