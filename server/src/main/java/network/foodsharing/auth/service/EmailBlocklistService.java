package network.foodsharing.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.EmailBlocklistEntry;
import network.foodsharing.auth.repository.EmailBlocklistRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.NoHistory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disposable/blocked email domains (parity: email blocklist). Pattern rules (case-insensitive):
 * <ul>
 *     <li>{@code mailinator.com} — the domain and every subdomain ({@code x.mailinator.com})</li>
 *     <li>{@code *} is a wildcard matched against the domain ({@code *.example.org}, {@code trash*.com})</li>
 *     <li>a pattern containing {@code @} is matched against the whole address ({@code spam@*}, {@code a@b.org})</li>
 * </ul>
 */
@Service
public class EmailBlocklistService {

    private final EmailBlocklistRepository repository;

    public EmailBlocklistService(EmailBlocklistRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return repository.findAll().stream()
                .map(EmailBlocklistEntry::getPattern)
                .anyMatch(pattern -> matches(pattern, email));
    }

    static boolean matches(String rawPattern, String rawEmail) {
        if (rawPattern == null || rawPattern.isBlank()) {
            return false;
        }
        String pattern = rawPattern.strip().toLowerCase(Locale.ROOT);
        String email = rawEmail.strip().toLowerCase(Locale.ROOT);
        int at = email.lastIndexOf('@');
        String domain = at < 0 ? email : email.substring(at + 1);
        if (pattern.startsWith("@")) {
            pattern = pattern.substring(1);
        }
        if (pattern.contains("@")) {
            return glob(pattern).matcher(email).matches();
        }
        if (pattern.contains("*")) {
            return glob(pattern).matcher(domain).matches();
        }
        return domain.equals(pattern) || domain.endsWith("." + pattern);
    }

    private static Pattern glob(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (String part : pattern.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    // ------------------------------------------------------------------ administration (C12, ORGA via admin)

    private static final Pattern VALID = Pattern.compile("([a-z0-9._%+*-]+@)?[a-z0-9*]([a-z0-9*-]*[a-z0-9*])?"
            + "(\\.[a-z0-9*]([a-z0-9*-]*[a-z0-9*])?)+");

    /** Lower-cased, stripped, without a leading {@code @}; null when not a domain or address pattern. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String pattern = raw.strip().toLowerCase(Locale.ROOT);
        if (pattern.startsWith("@")) {
            pattern = pattern.substring(1);
        }
        return pattern.length() >= 3 && pattern.length() <= 320 && VALID.matcher(pattern).matches() ? pattern : null;
    }

    @Transactional(readOnly = true)
    public List<EmailBlocklistEntry> list() {
        return repository.findAll(Sort.by("pattern", "id"));
    }

    /** @throws ApiException 400 VALIDATION_FAILED, 409 BLOCKLIST_DUPLICATE */
    @Transactional
    @NoHistory(reason = "admin.service.BlocklistAdminService records AdminAction.BLOCKLIST_ENTRY_ADDED")
    public EmailBlocklistEntry add(String rawPattern, String reason) {
        String pattern = requireValid(rawPattern);
        requireUnique(pattern, null);
        EmailBlocklistEntry entry = new EmailBlocklistEntry();
        entry.setPattern(pattern);
        entry.setReason(reason == null || reason.isBlank() ? null : reason.strip());
        return repository.save(entry);
    }

    /** Null arguments keep the value. @throws ApiException 404 BLOCKLIST_ENTRY_NOT_FOUND, 400, 409 */
    @Transactional
    @NoHistory(reason = "admin.service.BlocklistAdminService records AdminAction.BLOCKLIST_ENTRY_UPDATED")
    public EmailBlocklistEntry update(UUID entryId, String rawPattern, String reason) {
        EmailBlocklistEntry entry = require(entryId);
        if (rawPattern != null) {
            String pattern = requireValid(rawPattern);
            requireUnique(pattern, entryId);
            entry.setPattern(pattern);
        }
        if (reason != null) {
            entry.setReason(reason.isBlank() ? null : reason.strip());
        }
        return entry;
    }

    /** @throws ApiException 404 BLOCKLIST_ENTRY_NOT_FOUND */
    @Transactional
    @NoHistory(reason = "admin.service.BlocklistAdminService records AdminAction.BLOCKLIST_ENTRY_REMOVED")
    public EmailBlocklistEntry remove(UUID entryId) {
        EmailBlocklistEntry entry = require(entryId);
        repository.delete(entry);
        return entry;
    }

    private EmailBlocklistEntry require(UUID entryId) {
        return repository.findById(entryId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BLOCKLIST_ENTRY_NOT_FOUND));
    }

    private static String requireValid(String raw) {
        String pattern = normalize(raw);
        if (pattern == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "pattern");
        }
        return pattern;
    }

    private void requireUnique(String pattern, UUID self) {
        repository.findByPatternIgnoreCase(pattern).filter(other -> !other.getId().equals(self)).ifPresent(other -> {
            throw ApiException.conflict(ApiErrorCode.BLOCKLIST_DUPLICATE);
        });
    }
}
