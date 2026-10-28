package network.foodsharing.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.EventLocation;
import network.foodsharing.event.domain.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A36 event rules (upstream EventPermissions) as a matrix, plus the create/update validation. */
class EventRulesTest {

    static final UUID ME = UUID.randomUUID();
    static final UUID OTHER = UUID.randomUUID();
    static final Instant END = Instant.parse("2026-11-05T12:00:00Z");

    @ParameterizedTest(name = "orga={0} member={1} admin={2} author={3} public={4}")
    @CsvSource({
            // orga, member, admin, author, public -> see, create, edit, comment
            "false, false, false, false, false, false, false, false, false",
            "false, false, false, false, true,  true,  false, false, true",
            "false, true,  false, false, false, true,  true,  false, true",
            "false, true,  false, true,  false, true,  true,  true,  true",
            "false, true,  true,  false, false, true,  true,  true,  true",
            "true,  false, false, false, false, true,  false, true,  true",
            "false, false, false, true,  false, false, false, true,  false",
    })
    void matrix(boolean orga, boolean member, boolean admin, boolean author, boolean publicEvent,
                boolean see, boolean create, boolean edit, boolean comment) {
        EventRules.Actor actor = new EventRules.Actor(ME, orga, member, admin);
        UUID authorId = author ? ME : OTHER;
        assertThat(EventRules.maySee(actor, publicEvent)).isEqualTo(see);
        assertThat(EventRules.mayCreate(actor)).isEqualTo(create);
        assertThat(EventRules.mayEdit(actor, authorId)).isEqualTo(edit);
        assertThat(EventRules.mayComment(actor, publicEvent)).isEqualTo(comment);
    }

    @Test
    void rsvpClosesTwentyFourHoursAfterTheEnd() {
        EventRules.Actor member = new EventRules.Actor(ME, false, true, false);
        assertThat(EventRules.mayRsvp(member, false, END, END.minusSeconds(3600))).isTrue();
        assertThat(EventRules.mayRsvp(member, false, END, END.plusSeconds(24 * 3600))).isTrue();
        assertThat(EventRules.mayRsvp(member, false, END, END.plusSeconds(24 * 3600 + 1))).isFalse();
        EventRules.Actor stranger = new EventRules.Actor(ME, false, false, false);
        assertThat(EventRules.mayRsvp(stranger, false, END, END)).isFalse();
        assertThat(EventRules.mayRsvp(stranger, true, END, END)).isTrue();
    }

    @Test
    void wallPostsAreDeletedByTheirAuthorOrAnEditor() {
        EventRules.Actor member = new EventRules.Actor(ME, false, true, false);
        assertThat(EventRules.mayDeleteWallPost(member, OTHER, ME)).isTrue();
        assertThat(EventRules.mayDeleteWallPost(member, OTHER, OTHER)).isFalse();
        assertThat(EventRules.mayDeleteWallPost(member, ME, OTHER)).isTrue();
        assertThat(EventRules.mayDeleteWallPost(new EventRules.Actor(ME, false, true, true), OTHER, OTHER)).isTrue();
    }

    @Test
    void validationPerType() {
        EventLocation location = new EventLocation();
        location.setName("Fairteiler");
        assertCode(event(EventType.OFFLINE, END, END, null), location, ApiErrorCode.EVENT_DATES_INVALID);
        assertCode(event(EventType.OFFLINE, END.minusSeconds(60), END, null), null, ApiErrorCode.LOCATION_REQUIRED);
        EventService.validate(event(EventType.OFFLINE, END.minusSeconds(60), END, null), location);
        assertCode(event(EventType.ONLINE, END.minusSeconds(60), END, null), null, ApiErrorCode.ONLINE_LINK_REQUIRED);
        assertCode(event(EventType.ONLINE, END.minusSeconds(60), END, "http://meet.example"), null,
                ApiErrorCode.ONLINE_LINK_REQUIRED);
        EventService.validate(event(EventType.ONLINE, END.minusSeconds(60), END, "https://meet.example"), null);
        EventService.validate(event(EventType.OTHER, END.minusSeconds(60), END, null), null);
    }

    private static void assertCode(Event event, EventLocation location, ApiErrorCode code) {
        assertThatThrownBy(() -> EventService.validate(event, location)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(code));
    }

    private static Event event(EventType type, Instant start, Instant end, String link) {
        Event event = new Event();
        event.setType(type);
        event.setStartsAt(start);
        event.setEndsAt(end);
        event.setOnlineLink(link);
        return event;
    }
}
