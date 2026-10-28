package network.foodsharing.search.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.history.NoHistory;
import network.foodsharing.search.domain.SearchAction;
import network.foodsharing.search.repository.SearchIndexRepository;
import network.foodsharing.search.repository.SearchIndexRepository.Source;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains {@code search_index} (A45): a full rebuild (nightly job {@code search-reindex}, dev endpoint, first start
 * with an empty index) and an incremental refresh of every source row whose {@code updated_at} moved since the last
 * run (job {@code search-refresh}, every few minutes). Hard-deleted rows disappear with the next rebuild.
 */
@Service
public class SearchIndexer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private final SearchIndexRepository index;
    private final HistoryService history;
    private final Clock clock;
    private volatile Instant lastRefresh;

    public SearchIndexer(SearchIndexRepository index, HistoryService history, Clock clock) {
        this.index = index;
        this.history = history;
        this.clock = clock;
    }

    /** Lower case, without diacritics, whitespace collapsed ("Zürich  Café" → "zurich cafe"). */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String folded = value.replace("ß", "ss").replace("æ", "ae").replace("ø", "o").replace("œ", "oe");
        String stripped = MARKS.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    static String textOf(Source source) {
        return normalize(String.join(" ", nz(source.title()), nz(source.subtitle()), nz(source.extraText())));
    }

    /** Rebuilds the whole index. */
    @Transactional
    public int rebuild() {
        Instant now = clock.instant();
        index.deleteAll();
        int count = write(index.sources(null), now);
        lastRefresh = now;
        history.record(history.event(SearchAction.REINDEXED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("entries", count)).build());
        return count;
    }

    /** Rebuilds when the index is empty (first start, fresh seed); otherwise nothing. */
    @Transactional
    @NoHistory(reason = "derived index built on application start; explicit rebuilds record SEARCH REINDEXED")
    public boolean rebuildIfEmpty() {
        if (index.count() > 0) {
            return false;
        }
        rebuildWithoutHistory();
        return true;
    }

    /** Re-indexes source rows changed since the previous refresh / rebuild (a full rebuild on the first call). */
    @Transactional
    @NoHistory(reason = "incremental index maintenance every few minutes; full rebuilds record SEARCH_REINDEXED")
    public int refresh() {
        Instant since = lastRefresh;
        if (since == null) {
            return rebuildWithoutHistory();
        }
        Instant now = clock.instant();
        int count = write(index.sources(since.minusSeconds(5)), now);
        lastRefresh = now;
        return count;
    }

    private int rebuildWithoutHistory() {
        Instant now = clock.instant();
        index.deleteAll();
        int count = write(index.sources(null), now);
        lastRefresh = now;
        return count;
    }

    private int write(List<Source> sources, Instant now) {
        List<Source> keep = new ArrayList<>();
        Map<Source, String> texts = new HashMap<>();
        for (Source source : sources) {
            if (source.indexable() && source.title() != null && !source.title().isBlank()) {
                keep.add(source);
                texts.put(source, textOf(source));
            } else {
                index.remove(source.kind(), source.entityId());
            }
        }
        index.upsert(keep, texts, now);
        return keep.size();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
