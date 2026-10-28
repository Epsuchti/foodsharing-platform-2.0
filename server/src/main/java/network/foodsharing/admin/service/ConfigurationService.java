package network.foodsharing.admin.service;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.domain.ConfigurationSetting;
import network.foodsharing.admin.repository.ConfigurationSettingRepository;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime configuration (A43, C14): typed getters with the {@link ConfigurationKey} default when no row exists;
 * ORGA changes are validated per {@link ConfigurationKey.Type} and recorded as {@code CONFIGURATION_CHANGED}.
 */
@Service
public class ConfigurationService {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public record Entry(ConfigurationKey key, String value, String description, Instant updatedAt, UUID updatedBy) {
    }

    private final ConfigurationSettingRepository settings;
    private final CurrentActorService actors;
    private final HistoryService history;

    public ConfigurationService(ConfigurationSettingRepository settings, CurrentActorService actors,
                                HistoryService history) {
        this.settings = settings;
        this.actors = actors;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public String string(ConfigurationKey key) {
        return settings.findByConfigKey(key.key()).map(ConfigurationSetting::getConfigValue)
                .orElse(key.defaultValue());
    }

    public int integer(ConfigurationKey key) {
        try {
            return Integer.parseInt(string(key).strip());
        } catch (NumberFormatException ex) {
            return Integer.parseInt(key.defaultValue());
        }
    }

    public boolean bool(ConfigurationKey key) {
        return Boolean.parseBoolean(string(key).strip());
    }

    /** Empty = not set. */
    public String url(ConfigurationKey key) {
        String value = string(key).strip();
        return value.isEmpty() ? null : value;
    }

    public List<String> list(ConfigurationKey key) {
        return Arrays.stream(string(key).split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** ORGA: every key with its effective value. */
    @Transactional(readOnly = true)
    public List<Entry> entries() {
        actors.requireRole(Role.ORGA);
        Map<String, ConfigurationSetting> rows = settings.findAll().stream()
                .collect(Collectors.toMap(ConfigurationSetting::getConfigKey, Function.identity(), (a, b) -> a));
        return Arrays.stream(ConfigurationKey.values()).map(key -> entry(key, rows.get(key.key()))).toList();
    }

    /** @throws ApiException 403, 404 CONFIGURATION_KEY_NOT_FOUND, 400 CONFIGURATION_VALUE_INVALID */
    @Transactional
    public Entry update(String rawKey, String value) {
        actors.requireRole(Role.ORGA);
        ConfigurationKey key = ConfigurationKey.fromKey(rawKey)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.CONFIGURATION_KEY_NOT_FOUND));
        return set(key, value);
    }

    /** Validated write with history (also used by the donation settings). */
    @Transactional
    public Entry set(ConfigurationKey key, String value) {
        actors.requireRole(Role.ORGA);
        String normalized = normalize(key.type(), value);
        if (normalized == null) {
            throw ApiException.badRequest(ApiErrorCode.CONFIGURATION_VALUE_INVALID).withArg("key", key.key());
        }
        ConfigurationSetting row = settings.findByConfigKey(key.key()).orElseGet(() -> {
            ConfigurationSetting fresh = new ConfigurationSetting();
            fresh.setConfigKey(key.key());
            return fresh;
        });
        String old = row.getConfigValue() == null ? key.defaultValue() : row.getConfigValue();
        row.setConfigValue(normalized);
        ConfigurationSetting saved = settings.save(row);
        if (!old.equals(normalized)) {
            history.record(history.event(AdminAction.CONFIGURATION_CHANGED, HistoryService.SYSTEM_SUBJECT_ID)
                    .change(key.key(), old, normalized).build());
        }
        return entry(key, saved);
    }

    /** Normalised value, or null when invalid for the type. */
    static String normalize(ConfigurationKey.Type type, String raw) {
        if (raw == null || raw.length() > 4000) {
            return null;
        }
        String value = raw.strip();
        return switch (type) {
            case STRING -> value;
            case INTEGER -> value.matches("-?\\d{1,9}") ? String.valueOf(Integer.parseInt(value)) : null;
            case BOOLEAN -> value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")
                    ? value.toLowerCase(java.util.Locale.ROOT) : null;
            case EMAIL -> EMAIL.matcher(value).matches() ? value : null;
            case URL -> value.isEmpty() || validUrl(value) ? value : null;
            case LIST -> Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        };
    }

    private static boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Entry entry(ConfigurationKey key, ConfigurationSetting row) {
        return row == null ? new Entry(key, key.defaultValue(), null, null, null)
                : new Entry(key, row.getConfigValue(), row.getDescription(), row.getUpdatedAt(), row.getUpdatedBy());
    }
}
