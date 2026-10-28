package network.foodsharing.mailbox.service;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.Properties;
import java.util.function.Consumer;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.mailbox.domain.MailboxFetchState;
import network.foodsharing.mailbox.repository.MailboxFetchStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@code app.mailbox.inbound=imap}: fetches the catch-all IMAP folder through the {@code IMAP} breaker (A26/A35).
 * Messages with {@code uid > last_uid} (per UIDVALIDITY, {@code mailbox_fetch_state}) are handed over one by one,
 * then flagged deleted; the folder is expunged on close. Probe = connect + NOOP.
 */
@Component
@ConditionalOnProperty(name = "app.mailbox.inbound", havingValue = "imap")
public class ImapMailSource implements IncomingMailSource {

    static final String SOURCE = "imap";

    private static final Logger log = LoggerFactory.getLogger(ImapMailSource.class);

    private final AppProperties properties;
    private final IntegrationRegistry integrations;
    private final MailboxFetchStateRepository states;
    private final Clock clock;

    public ImapMailSource(AppProperties properties, IntegrationRegistry integrations,
                          MailboxFetchStateRepository states, Clock clock) {
        this.properties = properties;
        this.integrations = integrations;
        this.states = states;
        this.clock = clock;
        integrations.registerProbe(Integration.IMAP, this::probe);
    }

    @Override
    public int fetch(Consumer<byte[]> handler) {
        return integrations.call(Integration.IMAP, "imap-fetch", () -> doFetch(handler));
    }

    private int doFetch(Consumer<byte[]> handler) {
        MailboxFetchState state = states.findBySource(SOURCE).orElseGet(() -> {
            MailboxFetchState fresh = new MailboxFetchState();
            fresh.setSource(SOURCE);
            return fresh;
        });
        int count = 0;
        try (Store store = connect()) {
            Folder folder = store.getFolder(properties.getMailbox().getImap().getFolder());
            folder.open(Folder.READ_WRITE);
            try {
                UIDFolder uids = (UIDFolder) folder;
                long validity = uids.getUIDValidity();
                if (state.getUidValidity() == null || state.getUidValidity() != validity) {
                    state.setUidValidity(validity);
                    state.setLastUid(0);
                }
                for (Message message : uids.getMessagesByUID(state.getLastUid() + 1, UIDFolder.LASTUID)) {
                    long uid = uids.getUID(message);
                    if (uid <= state.getLastUid()) {
                        continue;
                    }
                    ByteArrayOutputStream raw = new ByteArrayOutputStream();
                    message.writeTo(raw);
                    try {
                        handler.accept(raw.toByteArray());
                    } catch (IllegalArgumentException ex) {
                        log.warn("Unparseable inbound mail uid {} deleted: {}", uid, ex.getMessage());
                    }
                    message.setFlag(Flags.Flag.DELETED, true);
                    state.setLastUid(uid);
                    count++;
                }
            } finally {
                folder.close(true);
            }
            state.setLastError(null);
        } catch (MessagingException | IOException ex) {
            state.setLastError(MailService.abbreviate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 2000));
            throw new IllegalStateException("IMAP fetch failed: " + ex.getMessage(), ex);
        } finally {
            state.setLastFetchAt(clock.instant());
            states.save(state);
        }
        return count;
    }

    private Store connect() throws MessagingException {
        AppProperties.Mailbox.Imap imap = properties.getMailbox().getImap();
        String protocol = imap.isSsl() ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail." + protocol + ".connectiontimeout", "10000");
        props.put("mail." + protocol + ".timeout", "30000");
        Store store = Session.getInstance(props).getStore(protocol);
        store.connect(imap.getHost(), imap.getPort(), imap.getUser(), imap.getPassword());
        return store;
    }

    private boolean probe() {
        try (Store store = connect()) {
            store.getFolder(properties.getMailbox().getImap().getFolder()).exists();
            return true;
        } catch (MessagingException ex) {
            return false;
        }
    }
}
