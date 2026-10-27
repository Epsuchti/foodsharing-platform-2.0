package network.foodsharing.auth.service;

import network.foodsharing.user.domain.User;

/** Result of a used magic link: the signed-in user, the new session (cookie already written), the stored return URL. */
public record MagicLinkLogin(User user, CreatedSession session, String returnUrl) {
}
