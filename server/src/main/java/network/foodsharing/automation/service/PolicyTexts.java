package network.foodsharing.automation.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;

/** Rule texts {@code automation.rule.*} in one language, plus the date formats of the reference. */
public final class PolicyTexts {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final MessageSource messages;
    private final Locale locale;

    public PolicyTexts(MessageSource messages, Locale locale) {
        this.messages = messages;
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    /** Text with MessageFormat arguments; without arguments the raw text (keeps {{placeholders}}). */
    public String text(String key, Object... args) {
        return messages.getMessage("automation.rule." + key, args.length == 0 ? null : args, locale);
    }

    public String days(long days) {
        return text(days == 1 ? "day.one" : "day.other", String.valueOf(days));
    }

    public String months(long months) {
        return text(months == 1 ? "month.one" : "month.other", String.valueOf(months));
    }

    public String dateTime(Instant instant, ZoneId zone) {
        return instant.atZone(zone).format(DATE_TIME);
    }

    /** "EEEE, dd.MM.yyyy HH:mm" in this language ({{pickupDate}}). */
    public String longDateTime(Instant instant, ZoneId zone) {
        Locale format = "gws".equals(locale.getLanguage()) ? Locale.forLanguageTag("de-CH") : locale;
        return instant.atZone(zone).format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy HH:mm", format));
    }

    /** "X", or "X, Y oder Z". */
    public String alternatives(List<String> names) {
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " " + text("or") + " "
                + names.get(names.size() - 1);
    }
}
