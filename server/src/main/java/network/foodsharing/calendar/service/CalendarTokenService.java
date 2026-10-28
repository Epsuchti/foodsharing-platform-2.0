package network.foodsharing.calendar.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.calendar.domain.CalendarAction;
import network.foodsharing.calendar.domain.CalendarToken;
import network.foodsharing.calendar.repository.CalendarTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal calendar feed token (A11): 32 random hex characters, one per user; regenerating replaces the old URL.
 * History never carries the token. Deleted with the account; the export only says whether a feed exists.
 */
@Service
public class CalendarTokenService implements AccountDeletionContributor, DataExportContributor {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Token and feed URL. */
    public record Feed(String token, String url) {
    }

    private final CalendarTokenRepository tokens;
    private final CurrentActorService actors;
    private final HistoryService history;
    private final AppProperties properties;

    public CalendarTokenService(CalendarTokenRepository tokens, CurrentActorService actors, HistoryService history,
                                AppProperties properties) {
        this.tokens = tokens;
        this.actors = actors;
        this.history = history;
        this.properties = properties;
    }

    static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    String url(String token) {
        String base = properties.getBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/api/public/calendar/" + token
                + ".ics";
    }

    /** @throws ApiException 404 CALENDAR_TOKEN_NOT_FOUND */
    @Transactional(readOnly = true)
    public Feed get() {
        return tokens.findByUserId(actors.requireUserId()).map(t -> new Feed(t.getToken(), url(t.getToken())))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.CALENDAR_TOKEN_NOT_FOUND));
    }

    /** Creates the token or replaces it (the old feed URL stops working). */
    @Transactional
    public Feed regenerate() {
        UUID me = actors.requireUserId();
        CalendarToken token = tokens.findByUserId(me).orElseGet(() -> {
            CalendarToken created = new CalendarToken();
            created.setUserId(me);
            return created;
        });
        boolean replaced = token.getId() != null;
        token.setToken(newToken());
        tokens.save(token);
        history.record(history.event(CalendarAction.TOKEN_CREATED, me).metadata("replaced", replaced).build());
        return new Feed(token.getToken(), url(token.getToken()));
    }

    @Transactional
    public void delete() {
        UUID me = actors.requireUserId();
        tokens.findByUserId(me).ifPresent(token -> {
            tokens.delete(token);
            history.record(history.event(CalendarAction.TOKEN_DELETED, me).build());
        });
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        tokens.findByUserId(userId).ifPresent(tokens::delete);
    }

    @Override
    public String section() {
        return "calendar";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        tokens.findByUserId(userId).ifPresentOrElse(t -> {
            result.put("feedEnabled", true);
            result.put("createdAt", t.getCreatedAt());
        }, () -> result.put("feedEnabled", false));
        return result;
    }
}
