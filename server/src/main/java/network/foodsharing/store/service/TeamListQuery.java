package network.foodsharing.store.service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import network.foodsharing.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * One page of a store's people (team tab and per-member statistics, resume item 14): status filter, name search
 * (case- and accent-insensitive), sort with a stable order (decision 138: key, then display name case-insensitive,
 * then user id). Sorting happens in memory: pickup numbers live in the pickup module, and teams are bounded.
 */
public record TeamListQuery(Status status, String search, Sort sort, int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** MEMBER includes managers; MANAGER = members with isManager; FORMER only in the statistics. */
    public enum Status { ALL, MANAGER, MEMBER, JUMPER, FORMER }

    /** NAME: managers first, then name. PICKUP_COUNT and LAST_PICKUP descending, no pickup last. */
    public enum Sort { NAME, PICKUP_COUNT, LAST_PICKUP }

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    public TeamListQuery {
        status = status == null ? Status.ALL : status;
        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), MAX_SIZE);
    }

    public static TeamListQuery of(Status status, String search, Sort sort, Integer page, Integer size) {
        return new TeamListQuery(status, search, sort, page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    /** Lower case, accents and spacing folded, so "Müller" matches "muller" and "MÜL". */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String folded = value.replace("ß", "ss").replace("æ", "ae").replace("ø", "o").replace("œ", "oe");
        return MARKS.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /** True when the user's display name contains the search (blank search matches everyone). */
    boolean matches(User user) {
        String needle = normalize(search);
        return needle.isEmpty() || normalize(user.getDisplayName()).contains(needle);
    }

    /** Name (case-insensitive), then user id: the tiebreaker of every sort. */
    static <T> Comparator<T> byName(Function<T, User> user) {
        return Comparator.comparing((T v) -> displayName(user.apply(v)), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(v -> user.apply(v).getId());
    }

    private static String displayName(User user) {
        return user.getDisplayName() == null ? "" : user.getDisplayName();
    }

    /** The requested page of an already filtered and sorted list. */
    <T> Page<T> page(List<T> sorted) {
        int from = (int) Math.min(sorted.size(), (long) page * size);
        return new PageImpl<>(sorted.subList(from, Math.min(sorted.size(), from + size)), PageRequest.of(page, size),
                sorted.size());
    }
}
