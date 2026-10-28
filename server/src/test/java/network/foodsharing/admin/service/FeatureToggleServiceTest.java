package network.foodsharing.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.domain.FeatureToggleSetting;
import network.foodsharing.admin.repository.FeatureToggleSettingRepository;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.MutableClock;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.testsupport.FixedClockConfig;
import org.junit.jupiter.api.Test;

/** A43/C11: defaults, stored overrides, 30 s cache, requireEnabled → 404 FEATURE_DISABLED, ORGA changes. */
class FeatureToggleServiceTest {

    FeatureToggleSettingRepository repository = mock(FeatureToggleSettingRepository.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    MutableClock clock = new FixedClockConfig().clock();
    FeatureToggleService service = new FeatureToggleService(repository, actors, history, clock);

    static FeatureToggleSetting row(FeatureToggle toggle, boolean enabled) {
        FeatureToggleSetting row = new FeatureToggleSetting();
        row.setToggleKey(toggle.name());
        row.setEnabled(enabled);
        return row;
    }

    @Test
    void missingRowsUseTheDefaultStoredRowsWin() {
        when(repository.findAll()).thenReturn(List.of(row(FeatureToggle.BASKETS, false)));
        assertThat(service.isEnabled(FeatureToggle.BASKETS)).isFalse();
        assertThat(service.isEnabled(FeatureToggle.BLOG)).isTrue();
        assertThat(service.isEnabled(FeatureToggle.DONATION_MODAL)).isFalse();
        assertThat(service.states()).hasSize(FeatureToggle.values().length);
    }

    @Test
    void statesAreCachedForThirtySeconds() {
        when(repository.findAll()).thenReturn(List.of());
        service.isEnabled(FeatureToggle.BASKETS);
        service.isEnabled(FeatureToggle.BLOG);
        verify(repository, times(1)).findAll();
        clock.fixAt(FixedClockConfig.SEED_NOW.plusSeconds(31));
        service.isEnabled(FeatureToggle.BASKETS);
        verify(repository, times(2)).findAll();
    }

    @Test
    void requireEnabledAnswers404FeatureDisabled() {
        when(repository.findAll()).thenReturn(List.of(row(FeatureToggle.BASKETS, false)));
        assertThatThrownBy(() -> service.requireEnabled(FeatureToggle.BASKETS))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ApiErrorCode.FEATURE_DISABLED);
                    assertThat(ex.getStatus().value()).isEqualTo(404);
                });
        service.requireEnabled(FeatureToggle.BLOG);
    }

    @Test
    void setRecordsHistoryClearsTheCacheAndRejectsUnknownKeys() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.isEnabled(FeatureToggle.BLOG)).isTrue();
        when(repository.findByToggleKey("BLOG")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var state = service.set("BLOG", false);
        assertThat(state.enabled()).isFalse();
        verify(history).event(AdminAction.FEATURE_TOGGLE_CHANGED, HistoryService.SYSTEM_SUBJECT_ID);
        when(repository.findAll()).thenReturn(List.of(row(FeatureToggle.BLOG, false)));
        assertThat(service.isEnabled(FeatureToggle.BLOG)).isFalse();
        assertThatThrownBy(() -> service.set("NOPE", true)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.FEATURE_TOGGLE_NOT_FOUND));
    }
}
