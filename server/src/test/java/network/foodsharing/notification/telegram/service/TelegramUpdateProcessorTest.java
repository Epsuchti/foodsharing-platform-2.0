package network.foodsharing.notification.telegram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import network.foodsharing.notification.telegram.repository.TelegramOffsetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A28: idempotence by update_id (stored offset), commands, group chats remembered. */
class TelegramUpdateProcessorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
    private final AtomicLong offset = new AtomicLong(0);
    private TelegramLinkService links;
    private TelegramSeenChats seen;
    private TelegramUpdateProcessor processor;

    @BeforeEach
    void setUp() {
        TelegramOffsetRepository offsets = mock(TelegramOffsetRepository.class);
        when(offsets.lastUpdateId()).thenAnswer(i -> offset.get() == 0 ? Optional.empty() : Optional.of(offset.get()));
        doAnswer(i -> {
            offset.set(i.getArgument(0));
            return null;
        }).when(offsets).save(anyLong(), any());
        links = mock(TelegramLinkService.class);
        seen = new TelegramSeenChats(clock);
        processor = new TelegramUpdateProcessor(offsets, links, seen, clock);
    }

    @Test
    void sameUpdateIsProcessedOnce() {
        TelegramClient.Update start = new TelegramClient.Update(10, "42", "private", null, "/start abc");
        assertThat(processor.process(start)).isTrue();
        assertThat(processor.process(start)).isFalse();
        assertThat(processor.process(new TelegramClient.Update(9, "42", "private", null, "/stop"))).isFalse();
        verify(links, times(1)).start("42", "abc");
        verify(links, never()).stop(any());
        assertThat(processor.lastUpdateId()).isEqualTo(10);
        assertThat(processor.nextSyntheticUpdateId()).isEqualTo(11);
    }

    @Test
    void batchSkipsSeenUpdatesAndKeepsGoingAfterAFailure() {
        when(links.stop("1")).thenThrow(new IllegalStateException("boom"));
        int processed = processor.processAll(List.of(
                new TelegramClient.Update(1, "1", "private", null, "/stop"),
                new TelegramClient.Update(2, "2", "private", null, "/stop@foodsharing_bot"),
                new TelegramClient.Update(2, "2", "private", null, "/stop")));
        assertThat(processed).isEqualTo(1);
        verify(links).stop("2");
    }

    @Test
    void groupChatsAreRememberedNotAnswered() {
        processor.process(new TelegramClient.Update(5, "-100", "supergroup", "Fairteiler", "/start x"));
        assertThat(seen.recent()).extracting(TelegramSeenChats.SeenChat::title).containsExactly("Fairteiler");
        verify(links, never()).start(any(), any());
        verify(links, never()).help(any());
    }

    @Test
    void otherTextGetsHelp() {
        processor.process(new TelegramClient.Update(7, "42", "private", null, "hallo"));
        processor.process(new TelegramClient.Update(8, "42", "private", null, "/start"));
        verify(links, times(2)).help("42");
        assertThat(TelegramUpdateProcessor.command("/START@bot  tok")).isEqualTo("/start");
    }
}
