package network.foodsharing.common.integration;

/**
 * SPI (A35): hands a raw inbound MIME message to the mailbox module without a dependency on it (the dev endpoint
 * {@code POST /api/dev/mailbox/incoming} uses it; nobody may depend on {@code mailbox}).
 */
public interface InboundMailHandler {

    /** @throws IllegalArgumentException when {@code raw} is no MIME message */
    void accept(byte[] raw);
}
