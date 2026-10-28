package network.foodsharing.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.mailbox.domain.MailboxFetchState;
import network.foodsharing.mailbox.repository.MailboxFetchStateRepository;
import network.foodsharing.mailbox.service.ImapMailSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** A35: the IMAP source fetches new messages once, advances the UID state and deletes them on the server. */
class ImapMailSourceIT {

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.IMAP);

    @Test
    @SuppressWarnings("unchecked")
    void fetchesEachMessageOnceAndDeletesIt() throws Exception {
        GreenMailUser user = GREEN_MAIL.setUser("catchall@local.test", "catchall", "secret");
        user.deliver(GreenMailUtil.createTextEmail("zuerich@local.test", "a@example.ch", "Eins", "Text 1",
                ServerSetupTest.SMTP));
        user.deliver(GreenMailUtil.createTextEmail("zuerich@local.test", "b@example.ch", "Zwei", "Text 2",
                ServerSetupTest.SMTP));

        AppProperties properties = new AppProperties();
        AppProperties.Mailbox.Imap imap = properties.getMailbox().getImap();
        imap.setHost("127.0.0.1");
        imap.setPort(ServerSetupTest.IMAP.getPort());
        imap.setUser("catchall");
        imap.setPassword("secret");
        imap.setSsl(false);
        IntegrationRegistry integrations = mock(IntegrationRegistry.class);
        when(integrations.call(eq(Integration.IMAP), any(), any()))
                .thenAnswer(i -> ((Supplier<Object>) i.getArgument(2)).get());
        MailboxFetchStateRepository states = mock(MailboxFetchStateRepository.class);
        List<MailboxFetchState> saved = new ArrayList<>();
        when(states.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(states.findBySource("imap")).thenAnswer(i -> saved.isEmpty() ? Optional.empty()
                : Optional.of(saved.get(saved.size() - 1)));
        ImapMailSource source = new ImapMailSource(properties, integrations, states, Clock.systemUTC());

        List<String> received = new ArrayList<>();
        assertThat(source.fetch(raw -> received.add(new String(raw)))).isEqualTo(2);
        assertThat(received).hasSize(2);
        assertThat(received.get(0)).contains("Subject: Eins");
        MailboxFetchState state = saved.get(saved.size() - 1);
        assertThat(state.getLastUid()).isPositive();
        assertThat(state.getUidValidity()).isNotNull();
        assertThat(state.getLastError()).isNull();

        assertThat(source.fetch(raw -> received.add(new String(raw)))).isZero();
        assertThat(GREEN_MAIL.getManagers().getImapHostManager().getInbox(user).getMessageCount()).isZero();
    }
}
