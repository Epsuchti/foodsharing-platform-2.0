package network.foodsharing.mailbox.service;

import java.util.function.Consumer;

/**
 * SPI (A35): where inbound mails come from. {@link ImapMailSource} ({@code app.mailbox.inbound=imap}) or
 * {@link LogImapMailSource} (default; local/e2e/test fetch nothing — use the dev endpoint or the webhook).
 */
public interface IncomingMailSource {

    /**
     * Fetches new raw messages and hands each to {@code handler}; a message is removed at the source only after the
     * handler returned normally.
     *
     * @return number of messages handed over
     */
    int fetch(Consumer<byte[]> handler);
}
