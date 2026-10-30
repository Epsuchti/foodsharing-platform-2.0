package network.foodsharing.admin.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.domain.FeatureToggleSetting;
import network.foodsharing.admin.repository.FeatureToggleSettingRepository;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.seed.InMemoryState;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feature toggles (A43, C11): a missing row means {@link FeatureToggle#defaultEnabled()}; states are cached for
 * 30 s (a change on this node clears the cache at once). Modules call {@link #requireEnabled} in their services:
 * a disabled feature answers 404 {@code FEATURE_DISABLED}.
 *
 * <p>The cache expiry is measured on a monotonic ticker ({@link System#nanoTime()}), not on the business
 * {@code Clock}: the e2e profile fixes that clock, so a clock-based cache would never expire there. The cache is an
 * {@link InMemoryState}, so {@code POST /api/dev/seed/reset} clears it together with the database (A59).
 */
@Service
public class FeatureToggleService implements InMemoryState {

    static final Duration CACHE = Duration.ofSeconds(30);

    public record ToggleState(FeatureToggle toggle, boolean enabled, String description, Instant updatedAt) {
    }

    private record Snapshot(Map<FeatureToggle, Boolean> states, long loadedAtNanos) {
    }

    private final FeatureToggleSettingRepository settings;
    private final CurrentActorService actors;
    private final HistoryService history;
    private final LongSupplier ticker;
    private volatile Snapshot cached;

    @Autowired
    public FeatureToggleService(FeatureToggleSettingRepository settings, CurrentActorService actors,
                                HistoryService history) {
        this(settings, actors, history, System::nanoTime);
    }

    /** {@code ticker}: monotonic nanoseconds (tests pass their own). */
    FeatureToggleService(FeatureToggleSettingRepository settings, CurrentActorService actors,
                         HistoryService history, LongSupplier ticker) {
        this.settings = settings;
        this.actors = actors;
        this.history = history;
        this.ticker = ticker;
    }

    @Override
    public void resetInMemoryState() {
        cached = null;
    }

    public boolean isEnabled(FeatureToggle toggle) {
        return states().get(toggle);
    }

    /** @throws ApiException 404 FEATURE_DISABLED (args.feature) */
    public void requireEnabled(FeatureToggle toggle) {
        if (!isEnabled(toggle)) {
            throw ApiException.notFound(ApiErrorCode.FEATURE_DISABLED).withArg("feature", toggle.name());
        }
    }

    /** Every toggle with its effective state (public bootstrap, cached). */
    public Map<FeatureToggle, Boolean> states() {
        Snapshot current = cached;
        long now = ticker.getAsLong();
        if (current == null || now - current.loadedAtNanos() > CACHE.toNanos()) {
            current = new Snapshot(load(), now);
            cached = current;
        }
        return current.states();
    }

    /** ORGA list with descriptions. */
    @Transactional(readOnly = true)
    public List<ToggleState> list() {
        actors.requireRole(Role.ORGA);
        Map<String, FeatureToggleSetting> rows = new LinkedHashMap<>();
        settings.findAll().forEach(row -> rows.put(row.getToggleKey(), row));
        return Arrays.stream(FeatureToggle.values()).map(toggle -> {
            FeatureToggleSetting row = rows.get(toggle.name());
            return row == null ? new ToggleState(toggle, toggle.defaultEnabled(), null, null)
                    : new ToggleState(toggle, row.isEnabled(), row.getDescription(), row.getUpdatedAt());
        }).toList();
    }

    /** @throws ApiException 403 (not ORGA), 404 FEATURE_TOGGLE_NOT_FOUND */
    @Transactional
    public ToggleState set(String key, boolean enabled) {
        actors.requireRole(Role.ORGA);
        FeatureToggle toggle = parse(key).orElseThrow(() -> ApiException.notFound(ApiErrorCode.FEATURE_TOGGLE_NOT_FOUND));
        FeatureToggleSetting row = settings.findByToggleKey(toggle.name()).orElseGet(() -> {
            FeatureToggleSetting fresh = new FeatureToggleSetting();
            fresh.setToggleKey(toggle.name());
            fresh.setEnabled(toggle.defaultEnabled());
            return fresh;
        });
        boolean old = row.isEnabled();
        row.setEnabled(enabled);
        FeatureToggleSetting saved = settings.save(row);
        history.record(history.event(AdminAction.FEATURE_TOGGLE_CHANGED, HistoryService.SYSTEM_SUBJECT_ID)
                .change(toggle.name(), old, enabled).metadata("toggle", toggle.name()).build());
        cached = null;
        return new ToggleState(toggle, enabled, saved.getDescription(), saved.getUpdatedAt());
    }

    private Map<FeatureToggle, Boolean> load() {
        Map<FeatureToggle, Boolean> result = new EnumMap<>(FeatureToggle.class);
        for (FeatureToggle toggle : FeatureToggle.values()) {
            result.put(toggle, toggle.defaultEnabled());
        }
        for (FeatureToggleSetting row : settings.findAll()) {
            parse(row.getToggleKey()).ifPresent(toggle -> result.put(toggle, row.isEnabled()));
        }
        return result;
    }

    private static Optional<FeatureToggle> parse(String key) {
        return Arrays.stream(FeatureToggle.values()).filter(t -> t.name().equals(key)).findFirst();
    }
}
