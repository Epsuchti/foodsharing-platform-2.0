package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import org.junit.jupiter.api.Test;

/** iCal output (escaping, events); the iCal import is gone (EA16, decision 169). */
class IcalServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    @Test
    void rendersEscapedEvents() {
        IntroPickupIcalService ical = new IntroPickupIcalService(null, null, null, null, null, null,
                Clock.fixed(NOW, ZoneOffset.UTC));
        IntroPickup pickup = new IntroPickup();
        pickup.setId(UUID.fromString("00000030-0001-4000-8000-000000000001"));
        pickup.setCategory(IntroPickupCategory.BAKERY);
        pickup.setStartAt(Instant.parse("2026-11-04T17:00:00Z"));
        pickup.setBusinessName("Babu's Bakery");
        pickup.setPrivateAddress("Löwenstrasse 1, 8001 Zürich");
        pickup.setPrivateInfo("Klingeln; Kisten mitbringen\nDanke");
        String feed = ical.render(List.of(pickup));
        assertThat(feed).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(feed).contains("UID:00000030-0001-4000-8000-000000000001@foodsharing.network\r\n");
        assertThat(feed).contains("DTSTART:20261104T170000Z\r\n").contains("DTEND:20261104T180000Z\r\n");
        assertThat(feed).contains("SUMMARY:Babu's Bakery (EinAb BAKERY)\r\n");
        assertThat(feed).contains("LOCATION:Löwenstrasse 1\\, 8001 Zürich\r\n");
        assertThat(feed).contains("DESCRIPTION:Klingeln\\; Kisten mitbringen\\nDanke\r\n");
        assertThat(IntroPickupIcalService.escape("a\\b")).isEqualTo("a\\\\b");
    }
}
