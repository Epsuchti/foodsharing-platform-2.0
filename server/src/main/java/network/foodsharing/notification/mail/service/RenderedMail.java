package network.foodsharing.notification.mail.service;

import network.foodsharing.api.model.Language;

/** Subject and bodies of a rendered template; {@code language} is the one actually used (after fallback). */
public record RenderedMail(String subject, String textBody, String htmlBody, Language language) {
}
