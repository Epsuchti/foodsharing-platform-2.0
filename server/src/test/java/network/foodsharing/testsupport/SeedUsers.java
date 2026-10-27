package network.foodsharing.testsupport;

import java.util.Arrays;
import java.util.UUID;
import network.foodsharing.api.model.Role;

/**
 * The seed users of plan §7.3 with their fixed ids (docs/seed-ids.md). T3 loads the same ids into the seed
 * database; slice tests use them as principals via {@link WithSeedUser}.
 */
public enum SeedUsers {
    NEWBIE(1, Role.FOODSHARER),
    PROFILE(2, Role.FOODSHARER),
    QUIZ(3, Role.FOODSHARER),
    EINAB1(4, Role.FOODSHARER),
    PENDING(5, Role.FOODSHARER),
    FOODSAVER(6, Role.FOODSAVER),
    JUMPER(7, Role.FOODSAVER),
    SLEEPY(8, Role.FOODSAVER),
    BIEB(9, Role.STORE_MANAGER),
    GUIDE(10, Role.FOODSAVER),
    BOT(11, Role.AMBASSADOR),
    WELCOME(12, Role.AMBASSADOR),
    REPORTS(13, Role.AMBASSADOR),
    ORGA(14, Role.ORGA);

    /** Fixed session id used by {@link WithSeedUser} principals. */
    public static final UUID TEST_SESSION_ID = UUID.fromString("00000002-0001-4000-8000-000000000001");

    private final UUID id;
    private final Role role;

    SeedUsers(int sequence, Role role) {
        this.id = UUID.fromString(String.format("00000001-0001-4000-8000-%012d", sequence));
        this.role = role;
    }

    public UUID id() {
        return id;
    }

    public Role role() {
        return role;
    }

    public String email() {
        return name().toLowerCase() + "@local";
    }

    public static SeedUsers byName(String name) {
        return Arrays.stream(values()).filter(user -> user.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown seed user " + name));
    }
}
