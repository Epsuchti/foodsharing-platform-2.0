package network.foodsharing.auth.service;

import java.util.Locale;
import java.util.regex.Pattern;
import network.foodsharing.auth.domain.EmailBlocklistEntry;
import network.foodsharing.auth.repository.EmailBlocklistRepository;
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
}
