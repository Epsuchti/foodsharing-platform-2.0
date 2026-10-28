package network.foodsharing.common.ics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private static final Instant START = Instant.parse("2026-11-03T17:30:00Z");

    @Test
    void writesAnEventWithUtcTimes() {
        String ics = IcsWriter.calendar("-//foodsharing//platform//DE", "Events", List.of(new IcsWriter.IcsEvent(
                "e1@foodsharing.network", START, START.plusSeconds(3600), "Treffen", null, "Zürich", "https://x.test/e/1",
                Instant.parse("2026-10-01T00:00:00Z"))));
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n")
                .contains("UID:e1@foodsharing.network\r\n", "DTSTART:20261103T173000Z\r\n", "DTEND:20261103T183000Z\r\n",
                        "DTSTAMP:20261001T000000Z\r\n", "LOCATION:Zürich\r\n", "URL:https://x.test/e/1\r\n")
                .doesNotContain("DESCRIPTION")
                .endsWith("END:VEVENT\r\nEND:VCALENDAR\r\n");
    }

    @Test
    void escapesText() {
        assertThat(IcsWriter.escape("a,b;c\\d\nline")).isEqualTo("a\\,b\\;c\\\\d\\nline");
        assertThat(IcsWriter.escape(null)).isEmpty();
    }

    @Test
    void foldsLongLinesAt75OctetsWithoutSplittingCharacters() {
        String line = "DESCRIPTION:" + "ä".repeat(100);
        String folded = IcsWriter.fold(line);
        String[] parts = folded.split("\r\n");
        assertThat(parts.length).isGreaterThan(2);
        for (String part : parts) {
            assertThat(part.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        for (int i = 1; i < parts.length; i++) {
            assertThat(parts[i]).startsWith(" ");
        }
        assertThat(folded.replace("\r\n ", "")).isEqualTo(line);
    }

    @Test
    void shortLinesAreNotFolded() {
        assertThat(IcsWriter.fold("SUMMARY:Hi")).isEqualTo("SUMMARY:Hi");
    }
}
