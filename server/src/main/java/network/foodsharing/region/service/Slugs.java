package network.foodsharing.region.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL slugs for regions and groups ({@code Zürich Stadt} → {@code zuerich-stadt}). */
public final class Slugs {

    private Slugs() {
    }

    public static String slugify(String name) {
        String text = name == null ? "" : name.strip().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        text = text.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (text.length() > 90) {
            text = text.substring(0, 90).replaceAll("-+$", "");
        }
        return text.isEmpty() ? "region" : text;
    }

    /** {@code base}, else {@code base-2}, {@code base-3}, … until {@code taken} says no. */
    public static String unique(String base, Predicate<String> taken) {
        String candidate = base;
        for (int i = 2; taken.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
