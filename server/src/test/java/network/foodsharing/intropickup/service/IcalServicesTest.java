package network.foodsharing.intropickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import org.junit.jupiter.api.Test;

/** iCal import (allowed links, parsing, size cap, failures) and iCal output (escaping, events). */
class IcalServicesTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final List<String> HOSTS = List.of("foodsharing.network", "foodsharing.de");

    @Test
    void onlyHttpsLinksOnAllowedHosts() {
        assertThat(IcalImportService.isAllowedLink("https://foodsharing.network/api/ical/abc", HOSTS)).isTrue();
        assertThat(IcalImportService.isAllowedLink("https://beta.foodsharing.de/x.ics", HOSTS)).isTrue();
        assertThat(IcalImportService.isAllowedLink("http://foodsharing.network/x", HOSTS)).isFalse();
        assertThat(IcalImportService.isAllowedLink("https://evilfoodsharing.network/x", HOSTS)).isFalse();
        assertThat(IcalImportService.isAllowedLink("https://foodsharing.network.evil.com/x", HOSTS)).isFalse();
        assertThat(IcalImportService.isAllowedLink("https://user@foodsharing.network/x", HOSTS)).isFalse();
        assertThat(IcalImportService.isAllowedLink("https://foodsharing.network:8443/x", HOSTS)).isFalse();
        assertThat(IcalImportService.isAllowedLink("file:///etc/passwd", HOSTS)).isFalse();
        assertThat(IcalImportService.isAllowedLink("https://localhost/x", HOSTS)).isFalse();
        assertThat(IcalImportService.isAllowedLink(" ", HOSTS)).isFalse();
    }

    @Test
    void parsesFutureEventsWithFoldedLinesAndEscapes() throws IOException {
        String feed = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20261105T170000Z\r\nDTEND:20261105T180000Z\r\n"
                + "SUMMARY:Abholung Coop\\, Stauffacher\r\nLOCATION;LANGUAGE=de:Badener\r\n strasse 1\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nDTSTART;TZID=Europe/Zurich:20261104T080000\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nDTSTART:20261001T170000Z\r\nSUMMARY:past\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        var candidates = IcalImportService.parse(feed, NOW);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).startAt()).isEqualTo(Instant.parse("2026-11-04T07:00:00Z"));
        assertThat(candidates.get(0).summary()).isEqualTo("Imported event");
        assertThat(candidates.get(1).summary()).isEqualTo("Abholung Coop, Stauffacher");
        assertThat(candidates.get(1).location()).isEqualTo("Badenerstrasse 1");
        assertThat(candidates.get(1).endAt()).isEqualTo(Instant.parse("2026-11-05T18:00:00Z"));
    }

    @Test
    void refusedLinksTooLargeFeedsAndFailuresYieldNothing() {
        IcalImportService service = new IcalImportService(new AppProperties(), Clock.fixed(NOW, ZoneOffset.UTC));
        int[] calls = {0};
        service.setFetcher(uri -> {
            calls[0]++;
            return new ByteArrayInputStream(new byte[IcalImportService.MAX_FEED_BYTES + 1]);
        });
        assertThat(service.candidates("https://foodsharing.network/big.ics")).isEmpty();
        assertThat(calls[0]).isEqualTo(1);
        assertThat(service.candidates("https://example.com/x.ics")).isEmpty();
        assertThat(calls[0]).isEqualTo(1);
        service.setFetcher(uri -> {
            throw new IOException("Unexpected iCal response 302");
        });
        assertThat(service.candidates("https://foodsharing.network/redirect.ics")).isEmpty();
        service.setFetcher(uri -> new ByteArrayInputStream(("BEGIN:VEVENT\nDTSTART:20261110T100000Z\nSUMMARY:x\n"
                + "END:VEVENT\n").getBytes(StandardCharsets.UTF_8)));
        assertThat(service.candidates("https://foodsharing.network/ok.ics")).hasSize(1);
    }

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
