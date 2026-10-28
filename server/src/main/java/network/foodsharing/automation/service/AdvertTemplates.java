package network.foodsharing.automation.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholders of open-slot advert and late-cancellation texts (port of the reference {@code renderAdvertisement}):
 * {@code {{date}} {{dateDe}} {{weekday}} {{time}} {{datetime}} {{datetimeDe}} {{storeName}} {{freeSlots}} {{link}}}.
 */
public final class AdvertTemplates {

    public static final Set<String> VARIABLES = Set.of("date", "dateDe", "weekday", "time", "datetime", "datetimeDe",
            "storeName", "freeSlots", "link");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]*)}}");

    private AdvertTemplates() {
    }

    /** The first placeholder that is not supported, if any. */
    public static Optional<String> unknownVariable(List<String> texts) {
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                if (!VARIABLES.contains(matcher.group(1))) {
                    return Optional.of(matcher.group(1));
                }
            }
        }
        return Optional.empty();
    }

    /** What is filled into the placeholders. */
    public record Values(Instant pickupAt, ZoneId zone, String storeName, int freeSlots, String link) {
    }

    public static String render(String template, Values values) {
        ZonedDateTime at = values.pickupAt().atZone(values.zone());
        String time = at.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString();
        String dateDe = at.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN));
        String weekday = at.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN));
        return (template == null ? "" : template.strip())
                .replace("{{date}}", at.toLocalDate().toString())
                .replace("{{dateDe}}", dateDe)
                .replace("{{weekday}}", weekday)
                .replace("{{time}}", time)
                .replace("{{datetime}}", at.toLocalDate() + " " + time)
                .replace("{{datetimeDe}}", weekday + ", " + dateDe + " um " + time)
                .replace("{{storeName}}", values.storeName() == null ? "" : values.storeName())
                .replace("{{freeSlots}}", String.valueOf(values.freeSlots()))
                .replace("{{link}}", values.link() == null ? "" : values.link());
    }
}
