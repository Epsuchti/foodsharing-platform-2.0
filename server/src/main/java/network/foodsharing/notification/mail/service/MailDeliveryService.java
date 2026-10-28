package network.foodsharing.notification.mail.service;

import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.MailTransport;
import network.foodsharing.notification.mail.OutgoingMail;
import network.foodsharing.notification.mail.domain.MailAction;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import network.foodsharing.notification.mail.repository.MailOutboxRepository;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends one outbox mail through the active {@link MailTransport}. Failures are retried with exponential backoff
 * ({@code base × 2^(attempts − 1)}) until {@code app.mail.outbox-max-attempts}, then the mail is FAILED.
 */
@Service
public class MailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryService.class);

    private final MailOutboxRepository repository;
    private final ObjectProvider<MailTransport> transport;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;
    private final IntegrationRegistry integrations;
    private final UploadService uploads;

    public MailDeliveryService(MailOutboxRepository repository, ObjectProvider<MailTransport> transport,
                               HistoryService history, AppProperties properties, Clock clock,
                               IntegrationRegistry integrations, UploadService uploads) {
        this.integrations = integrations;
        this.uploads = uploads;
        this.repository = repository;
        this.transport = transport;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** Records MAIL_SENT on success, MAIL_FAILED when the last attempt failed. */
    @Transactional
    public void deliver(UUID mailId) {
        MailOutboxMessage message = repository.findById(mailId).orElse(null);
        if (message == null || message.getStatus() != MailStatus.PENDING) {
            return;
        }
        MailTransport active = transport.getIfAvailable();
        if (active == null) {
            log.warn("No MailTransport bean for app.mail.transport={}; mail {} stays pending",
                    properties.getMail().getTransport(), mailId);
            return;
        }
        Instant now = clock.instant();
        try {
            active.send(new OutgoingMail(message.getToAddress(), from(message), message.getSubject(),
                    message.getTextBody(), message.getHtmlBody(), headers(message.getHeaders()), message.getReplyTo(),
                    attachments(message.getAttachmentsJson())));
            message.setAttempts(message.getAttempts() + 1);
            message.setStatus(MailStatus.SENT);
            message.setSentAt(now);
            message.setLastError(null);
            history.record(history.event(MailAction.SENT, HistoryService.SYSTEM_SUBJECT_ID).actorSystem()
                    .metadata(metadata(message)).build());
        } catch (RuntimeException ex) {
            int attempts = message.getAttempts() + 1;
            message.setAttempts(attempts);
            message.setLastError(StringUtils.abbreviate(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 2000));
            if (attempts >= properties.getMail().getOutboxMaxAttempts()) {
                message.setStatus(MailStatus.FAILED);
                log.error("Mail {} ({}) failed permanently after {} attempts: {}", mailId, message.getTemplate(),
                        attempts, ex.getMessage());
                history.record(history.event(MailAction.FAILED, HistoryService.SYSTEM_SUBJECT_ID).actorSystem()
                        .metadata(metadata(message)).build());
            } else {
                message.setNextAttemptAt(now.plus(backoff(attempts)));
                log.warn("Mail {} ({}) attempt {} failed, retrying at {}: {}", mailId, message.getTemplate(), attempts,
                        message.getNextAttemptAt(), ex.getMessage());
            }
        }
    }

    /** Admin integration test: one plain mail straight through the transport and the SMTP breaker (A26). */
    public void sendTest(String to) {
        MailTransport active = transport.getIfAvailable();
        if (active == null) {
            throw new IllegalStateException("No MailTransport for app.mail.transport=" + properties.getMail().getTransport());
        }
        integrations.run(Integration.SMTP, "smtp-test", () -> active.send(new OutgoingMail(to,
                properties.getMail().getFrom(), "foodsharing: SMTP test",
                "Test message from foodsharing: mail delivery works.", null)));
    }

    /** Delay before the next attempt after {@code attempts} failed ones: base × 2^(attempts − 1). */
    Duration backoff(int attempts) {
        long factor = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        return Duration.ofMinutes(properties.getMail().getOutboxBackoffBaseMinutes()).multipliedBy(factor);
    }

    private static Map<String, Object> metadata(MailOutboxMessage message) {
        return Map.of("mailId", message.getId().toString(), "template", message.getTemplate(),
                "attempts", message.getAttempts());
    }

    /** The sender override of a mailbox mail ({@code "Name <address>"}), else {@code app.mail.from}. */
    private String from(MailOutboxMessage message) {
        if (message.getFromAddress() == null) {
            return properties.getMail().getFrom();
        }
        try {
            return new jakarta.mail.internet.InternetAddress(message.getFromAddress(), message.getFromName(), "UTF-8")
                    .toString();
        } catch (java.io.UnsupportedEncodingException ex) {
            return message.getFromAddress();
        }
    }

    /** Upload ids (JSON string array) → lazily read attachments; a missing upload fails the attempt (retry). */
    private java.util.List<OutgoingMail.Attachment> attachments(String json) {
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<OutgoingMail.Attachment> result = new java.util.ArrayList<>();
        java.util.regex.Matcher ids = UUID_PATTERN.matcher(json);
        while (ids.find()) {
            Upload upload = uploads.find(UUID.fromString(ids.group()));
            result.add(new OutgoingMail.Attachment(upload.getFileName(), upload.getMimeType(), () -> {
                try (java.io.InputStream in = uploads.open(upload)) {
                    return in.readAllBytes();
                } catch (java.io.IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            }));
        }
        return result;
    }

    private static final java.util.regex.Pattern UUID_PATTERN = java.util.regex.Pattern
            .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** {@code Name: value} lines → header map. */
    static java.util.Map<String, String> headers(String stored) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (stored != null) {
            for (String line : stored.split("\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
                }
            }
        }
        return headers;
    }
}
