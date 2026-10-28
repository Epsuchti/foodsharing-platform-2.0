package network.foodsharing.common.seed;

/**
 * A bean with in-memory state (circuit breakers, caches, seen chats) that the dev seed reset puts back to its start
 * state ({@code POST /api/dev/seed/reset}), so e2e tests do not depend on each other.
 */
public interface InMemoryState {

    void resetInMemoryState();
}
