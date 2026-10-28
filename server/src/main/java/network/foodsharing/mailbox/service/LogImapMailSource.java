package network.foodsharing.mailbox.service;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code app.mailbox.inbound=log} (default): fetches nothing, logs at debug (A35). */
@Component
@ConditionalOnProperty(name = "app.mailbox.inbound", havingValue = "log", matchIfMissing = true)
public class LogImapMailSource implements IncomingMailSource {

    private static final Logger log = LoggerFactory.getLogger(LogImapMailSource.class);

    @Override
    public int fetch(Consumer<byte[]> handler) {
        log.debug("Inbound mail in log mode: nothing fetched (POST /api/dev/mailbox/incoming to inject mail)");
        return 0;
    }
}
