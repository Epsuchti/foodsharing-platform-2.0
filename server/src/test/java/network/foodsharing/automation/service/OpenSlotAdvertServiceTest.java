package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AdvertChannel;
import network.foodsharing.automation.repository.OpenSlotAdvertAuditRepository;
import network.foodsharing.automation.repository.OpenSlotAdvertRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.service.PickupScheduleService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Advert validation and placeholders (AU4, M4 B5). */
class OpenSlotAdvertServiceTest {

    OpenSlotAdvertService service = new OpenSlotAdvertService(mock(OpenSlotAdvertRepository.class),
            mock(OpenSlotAdvertAuditRepository.class), mock(PickupPolicyService.class),
            mock(PickupScheduleService.class), mock(HistoryService.class, RETURNS_DEEP_STUBS),
            JsonMapper.builder().build(), Clock.systemUTC());

    static OpenSlotAdvertService.AdvertInput input(Set<AdvertChannel> channels, List<String> store,
                                                   String telegramChatId, int hours) {
        return new OpenSlotAdvertService.AdvertInput(true, false, hours, channels, telegramChatId, store, List.of(),
                List.of("Telegram {{time}}"), false, null, 72, true);
    }

    static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException ex) {
            return ex.getCode();
        }
        return null;
    }

    @Test
    void validAdvertPasses() {
        service.validate(1, input(Set.of(AdvertChannel.STORE_CHAT), List.of("Frei {{datetimeDe}}"), null, 24));
    }

    @Test
    void rules() {
        assertThat(code(() -> service.validate(6, input(Set.of(AdvertChannel.STORE_CHAT), List.of("a"), null, 24))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(() -> service.validate(1, input(Set.of(AdvertChannel.STORE_CHAT), List.of("a"), null, 0))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(() -> service.validate(1, input(Set.of(), List.of("a"), null, 24))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(() -> service.validate(1, input(Set.of(AdvertChannel.PUSH), List.of("a"), null, 24))))
                .isEqualTo(ApiErrorCode.ADVERT_MESSAGES_MISSING);
        assertThat(code(() -> service.validate(1, input(Set.of(AdvertChannel.TELEGRAM), List.of(), null, 24))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(() -> service.validate(1, input(Set.of(AdvertChannel.TELEGRAM), List.of(), "-1002", 24))))
                .isNull();
        assertThat(code(() -> service.validate(1, input(Set.of(AdvertChannel.STORE_CHAT), List.of("{{nope}}"),
                null, 24)))).isEqualTo(ApiErrorCode.INVALID_TEMPLATE_VARIABLE);
    }

    @Test
    void lateCancellationMessageIsRequiredWhenEnabled() {
        var late = new OpenSlotAdvertService.AdvertInput(true, false, 24, Set.of(AdvertChannel.STORE_CHAT), null,
                List.of("a"), List.of(), List.of(), true, " ", 72, true);
        assertThatThrownBy(() -> service.validate(1, late)).isInstanceOf(ApiException.class);
    }

    @Test
    void placeholdersAreRendered() {
        var values = new AdvertTemplates.Values(Instant.parse("2026-11-12T17:30:00Z"), ZoneId.of("Europe/Zurich"),
                "Bäckerei", 2, "https://x/stores/1/pickups");
        assertThat(AdvertTemplates.render("{{weekday}} {{dateDe}} {{time}} | {{datetimeDe}} | {{storeName}} "
                + "{{freeSlots}} {{link}} {{date}} {{datetime}}", values))
                .isEqualTo("Donnerstag 12.11.2026 18:30 | Donnerstag, 12.11.2026 um 18:30 | Bäckerei 2 "
                        + "https://x/stores/1/pickups 2026-11-12 2026-11-12 18:30");
        assertThat(AdvertTemplates.unknownVariable(List.of("{{time}}", "{{x}}"))).contains("x");
        assertThat(OpenSlotAdvertService.channels("PUSH, TELEGRAM")).containsExactlyInAnyOrder(AdvertChannel.PUSH,
                AdvertChannel.TELEGRAM);
        assertThat(ZoneOffset.UTC).isNotNull();
    }
}
