package network.foodsharing.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.calendar.domain.CalendarToken;
import network.foodsharing.calendar.repository.CalendarTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A11: the own feed token, regenerated (the old URL stops working) and deleted (was CalendarFeedIT). */
class CalendarTokenServiceTest {

    static final UUID ME = UUID.randomUUID();

    CalendarTokenRepository tokens = mock(CalendarTokenRepository.class);
    CurrentActorService actors = mock(CurrentActorService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    AppProperties properties = new AppProperties();
    CalendarTokenService service = new CalendarTokenService(tokens, actors, history, properties);

    @BeforeEach
    void setUp() {
        when(actors.requireUserId()).thenReturn(ME);
        properties.setBaseUrl("https://foodsharing.test/");
    }

    @Test
    void withoutATokenTheFeedIs404() {
        when(tokens.findByUserId(ME)).thenReturn(Optional.empty());
        assertThatThrownBy(service::get).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.CALENDAR_TOKEN_NOT_FOUND));
        service.delete();
        verify(tokens, never()).delete(any());
    }

    @Test
    void regenerateReplacesTheTokenWithANew32HexOne() {
        CalendarToken existing = new CalendarToken();
        existing.setId(UUID.randomUUID());
        existing.setUserId(ME);
        existing.setToken("0123456789abcdef".repeat(4));
        when(tokens.findByUserId(ME)).thenReturn(Optional.of(existing));
        CalendarTokenService.Feed feed = service.regenerate();
        assertThat(feed.token()).matches("[0-9a-f]{32}").isNotEqualTo("0123456789abcdef".repeat(4));
        assertThat(existing.getToken()).isEqualTo(feed.token());
        assertThat(feed.url()).isEqualTo("https://foodsharing.test/api/public/calendar/" + feed.token() + ".ics");
        verify(tokens).save(existing);
        assertThat(service.get().token()).isEqualTo(feed.token());
        service.delete();
        verify(tokens).delete(existing);
    }
}
