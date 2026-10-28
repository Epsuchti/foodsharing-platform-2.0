package network.foodsharing.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.event.domain.Event;
import network.foodsharing.event.domain.EventLocation;
import network.foodsharing.event.domain.EventType;
import org.junit.jupiter.api.Test;

/** E3: RFC 5545 fields of an event export. */
class EventIcsServiceTest {

    @Test
    void writesOneVeventWithLocationAndLink() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://foodsharing.example/");
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
        EventIcsService service = new EventIcsService(null, properties, clock);
        Event event = new Event();
        event.setId(UUID.fromString("00000044-0002-4000-8000-000000000001"));
        event.setType(EventType.OFFLINE);
        event.setName("Brunch, mit Resten");
        event.setDescription("Zeile 1\nZeile 2");
        event.setStartsAt(Instant.parse("2026-11-05T09:00:00Z"));
        event.setEndsAt(Instant.parse("2026-11-05T12:00:00Z"));
        EventLocation location = new EventLocation();
        location.setName("Fairteiler Wiedikon");
        location.setStreet("Schlossgasse 10");
        location.setZip("8003");
        location.setCity("Zürich");

        String ics = service.write(null, List.of(new EventViews.EventItem(event, location, List.of(), null)));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").contains(
                "UID:event-00000044-0002-4000-8000-000000000001@foodsharing.network\r\n",
                "DTSTART:20261105T090000Z\r\n", "DTEND:20261105T120000Z\r\n", "DTSTAMP:20261102T090000Z\r\n",
                "SUMMARY:Brunch\\, mit Resten\r\n", "DESCRIPTION:Zeile 1\\nZeile 2\r\n",
                "LOCATION:Fairteiler Wiedikon\\, Schlossgasse 10\\, 8003 Zürich\r\n",
                "URL:https://foodsharing.example/events/00000044-0002-4000-8000-000000000001\r\n")
                .endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void onlineEventsUseTheLinkAsLocation() {
        AppProperties properties = new AppProperties();
        EventIcsService service = new EventIcsService(null, properties, Clock.systemUTC());
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setType(EventType.ONLINE);
        event.setName("Infoabend");
        event.setOnlineLink("https://meet.example/x");
        event.setStartsAt(Instant.parse("2026-11-04T17:00:00Z"));
        event.setEndsAt(Instant.parse("2026-11-04T18:30:00Z"));
        String ics = service.write("foodsharing", List.of(new EventViews.EventItem(event, null, List.of(), null)));
        assertThat(ics).contains("LOCATION:https://meet.example/x\r\n", "X-WR-CALNAME:foodsharing\r\n")
                .doesNotContain("DESCRIPTION:");
    }
}
