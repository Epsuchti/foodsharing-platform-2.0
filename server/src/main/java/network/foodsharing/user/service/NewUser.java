package network.foodsharing.user.service;

import java.time.Instant;
import network.foodsharing.api.model.Language;

/** Data for a new account (sign-up via Google or magic link, plan §8.3: only what the first step needs). */
public record NewUser(String email, Instant emailVerifiedAt, String displayName, String firstName, String lastName,
                      Language language, String avatarUrl) {
}
