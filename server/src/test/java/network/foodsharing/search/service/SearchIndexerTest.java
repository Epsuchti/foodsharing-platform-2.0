package network.foodsharing.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.search.domain.SearchAction;
import network.foodsharing.search.domain.SearchKind;
import network.foodsharing.search.repository.SearchIndexRepository;
import network.foodsharing.search.repository.SearchIndexRepository.Source;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * C5 index maintenance (formerly SearchFlowIT): the index is built on start when empty, the first refresh is a full
 * rebuild, later refreshes only re-index rows changed since the previous one (with 5 s overlap), rows that are no
 * longer indexable leave the index, and only an explicit rebuild is recorded as SEARCH REINDEXED.
 */
class SearchIndexerTest {

    static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z");
    static final Instant T1 = T0.plusSeconds(300);

    SearchIndexRepository index = mock(SearchIndexRepository.class);
    HistoryService history = mock(HistoryService.class);
    Clock clock = mock(Clock.class);
    SearchIndexer indexer = new SearchIndexer(index, history, clock);

    static Source store(String title, boolean indexable) {
        return new Source(SearchKind.STORE, UUID.randomUUID(), title, null, null, "Bahnhofstrasse 1", UUID.randomUUID(),
                "REGION_MEMBERS", "/stores/x", null, indexable);
    }

    @Test
    void refreshRebuildsFirstThenReindexesOnlyChangedRows() {
        when(clock.instant()).thenReturn(T0);
        Source kept = store("Quokkabäckerei", true);
        when(index.sources(null)).thenReturn(List.of(kept));
        assertThat(indexer.refresh()).isEqualTo(1);
        verify(index).deleteAll();

        when(clock.instant()).thenReturn(T1);
        Source closed = store("Geschlossen", false);
        when(index.sources(T0.minusSeconds(5))).thenReturn(List.of(kept, closed));
        assertThat(indexer.refresh()).isEqualTo(1);
        verify(index, times(1)).deleteAll();
        verify(index).remove(SearchKind.STORE, closed.entityId());
        ArgumentCaptor<Collection<Source>> written = ArgumentCaptor.captor();
        verify(index, times(2)).upsert(written.capture(), anyMap(), anyMap(), any());
        assertThat(written.getValue()).containsExactly(kept);
        verify(history, never()).record(any());
    }

    @Test
    void onlyAnExplicitRebuildIsRecorded() {
        when(clock.instant()).thenReturn(T0);
        when(history.event(any(), any())).thenAnswer(call -> HistoryEvent.builder(call.getArgument(0),
                call.getArgument(1)));
        when(index.sources(null)).thenReturn(List.of(store("A", true), store("B", true)));
        assertThat(indexer.rebuild()).isEqualTo(2);
        ArgumentCaptor<HistoryEvent> entry = ArgumentCaptor.forClass(HistoryEvent.class);
        verify(history).record(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(SearchAction.REINDEXED);
    }

    @Test
    void theStartBuildsTheIndexOnlyWhenEmpty() {
        when(clock.instant()).thenReturn(T0);
        when(index.count()).thenReturn(10L);
        assertThat(indexer.rebuildIfEmpty()).isFalse();
        verify(index, never()).deleteAll();
        when(index.count()).thenReturn(0L);
        assertThat(indexer.rebuildIfEmpty()).isTrue();
        verify(index).deleteAll();
        verify(index).upsert(anyCollection(), anyMap(), anyMap(), any());
    }
}
