package network.foodsharing.common.ics;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Minimal RFC 5545 writer (E3; no ical4j dependency): {@code VCALENDAR} with {@code VEVENT}s, UTC times, text
 * escaping and line folding at 75 octets. Lines end with CRLF.
 */
public final class IcsWriter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final int MAX_OCTETS = 75;

    /** One event; nullable fields are omitted. */
    public record IcsEvent(String uid, Instant start, Instant end, String summary, String description, String location,
                           String url, Instant stamp) {
    }

    private IcsWriter() {
    }

    public static String calendar(String productId, String calendarName, List<IcsEvent> events) {
        return calendar(productId, calendarName, events, List.of());
    }

    /** With one {@code VALARM} (display) per entry of {@code alarmMinutesBefore} on every event (A11 reminders). */
    public static String calendar(String productId, String calendarName, List<IcsEvent> events,
                                  List<Integer> alarmMinutesBefore) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:" + productId);
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        if (calendarName != null) {
            line(out, "X-WR-CALNAME:" + escape(calendarName));
        }
        for (IcsEvent event : events) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + event.uid());
            line(out, "DTSTAMP:" + UTC.format(event.stamp()));
            line(out, "DTSTART:" + UTC.format(event.start()));
            line(out, "DTEND:" + UTC.format(event.end()));
            line(out, "SUMMARY:" + escape(event.summary()));
            if (event.description() != null && !event.description().isBlank()) {
                line(out, "DESCRIPTION:" + escape(event.description()));
            }
            if (event.location() != null && !event.location().isBlank()) {
                line(out, "LOCATION:" + escape(event.location()));
            }
            if (event.url() != null && !event.url().isBlank()) {
                line(out, "URL:" + event.url());
            }
            for (Integer minutes : alarmMinutesBefore) {
                line(out, "BEGIN:VALARM");
                line(out, "ACTION:DISPLAY");
                line(out, "DESCRIPTION:" + escape(event.summary()));
                line(out, "TRIGGER:-PT" + minutes + "M");
                line(out, "END:VALARM");
            }
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    /** TEXT escaping (RFC 5545 3.3.11): backslash, semicolon, comma, newline. */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** Folds a content line into chunks of at most 75 octets (continuations start with a space), never splitting a character. */
    static String fold(String line) {
        StringBuilder out = new StringBuilder();
        int octets = 0;
        int limit = MAX_OCTETS;
        for (int i = 0; i < line.length(); ) {
            int codePoint = line.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int size = ch.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > limit) {
                out.append("\r\n ");
                octets = 0;
                limit = MAX_OCTETS - 1;
            }
            out.append(ch);
            octets += size;
            i += Character.charCount(codePoint);
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String content) {
        out.append(fold(content)).append("\r\n");
    }
}
