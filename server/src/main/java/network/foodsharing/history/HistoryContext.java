package network.foodsharing.history;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Carries the optional user-entered {@code reason} of a request to history recording (explicit
 * {@code HistoryService.record} calls and the {@link Tracked} listener). Controllers wrap the service call:
 * <pre>HistoryContext.withReason(request.getReason(), () -&gt; service.change(...));</pre>
 */
public final class HistoryContext {

    private static final ThreadLocal<String> REASON = new ThreadLocal<>();

    private HistoryContext() {
    }

    public static Optional<String> reason() {
        return Optional.ofNullable(REASON.get());
    }

    public static void withReason(String reason, Runnable action) {
        withReason(reason, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T withReason(String reason, Supplier<T> action) {
        String previous = REASON.get();
        if (reason == null || reason.isBlank()) {
            REASON.remove();
        } else {
            REASON.set(reason.strip());
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                REASON.remove();
            } else {
                REASON.set(previous);
            }
        }
    }
}
