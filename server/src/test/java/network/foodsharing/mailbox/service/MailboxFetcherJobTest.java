package network.foodsharing.mailbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.config.AppProperties;
import org.junit.jupiter.api.Test;

/** D6.3: the fetcher skips while the IMAP breaker is open and records only failures. */
class MailboxFetcherJobTest {

    private final IncomingMailSource source = mock(IncomingMailSource.class);
    private final IntegrationRegistry integrations = mock(IntegrationRegistry.class);
    private final MailboxFetcherJob job = new MailboxFetcherJob(mock(JobRunner.class), source,
            mock(IncomingMailProcessor.class), integrations, new AppProperties());

    @Test
    void skipsWhileTheBreakerIsOpen() {
        when(integrations.shouldSkip(Integration.IMAP)).thenReturn(true);
        job.runNow();
        verify(source, never()).fetch(any());
    }

    @Test
    void fetchesThroughTheSourceOtherwise() {
        job.runNow();
        verify(source).fetch(any());
        assertThat(job.jobName()).isEqualTo("mailbox-fetcher");
        assertThat(job.recordEveryRun()).isFalse();
    }
}
