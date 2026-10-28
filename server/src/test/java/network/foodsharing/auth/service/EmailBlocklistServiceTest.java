package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import network.foodsharing.auth.domain.EmailBlocklistEntry;
import network.foodsharing.auth.repository.EmailBlocklistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmailBlocklistServiceTest {

    @ParameterizedTest
    @CsvSource({
            // exact domain also blocks subdomains, case-insensitive
            "mailinator.com, someone@mailinator.com, true",
            "mailinator.com, someone@MAILINATOR.COM, true",
            "mailinator.com, someone@eu.mailinator.com, true",
            "mailinator.com, someone@notmailinator.com, false",
            "mailinator.com, someone@mailinator.com.evil, false",
            "@mailinator.com, someone@mailinator.com, true",
            // wildcards match the domain
            "*.example.org, a@sub.example.org, true",
            "*.example.org, a@example.org, false",
            "trash*.com, a@trashmail.com, true",
            "trash*.com, a@trash.com, true",
            "trash*.com, a@mytrash.com, false",
            // patterns with @ match the whole address
            "spam@*, spam@anything.ch, true",
            "spam@*, ham@anything.ch, false",
            "a@b.org, A@B.org, true",
            "a@b.org, aa@b.org, false",
            // regex characters in patterns are literal
            "example.invalid, x@example.invalid, true",
            "ex.mple.com, x@exampleXcom, false"
    })
    void matchesPatterns(String pattern, String email, boolean blocked) {
        assertThat(EmailBlocklistService.matches(pattern, email)).isEqualTo(blocked);
    }

    @Test
    void checksAllStoredPatterns() {
        EmailBlocklistRepository repository = mock(EmailBlocklistRepository.class);
        when(repository.findAll()).thenReturn(List.of(entry("mailinator.com"), entry("*.tempmail.test")));
        EmailBlocklistService service = new EmailBlocklistService(repository);

        assertThat(service.isBlocked("x@mailinator.com")).isTrue();
        assertThat(service.isBlocked("x@a.tempmail.test")).isTrue();
        assertThat(service.isBlocked("x@foodsharing.network")).isFalse();
        assertThat(service.isBlocked(null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "' Spam.Example ' | spam.example",
            "@trash.org | trash.org",
            "Bad@Example.ORG | bad@example.org",
            "*.tempmail.test | *.tempmail.test",
            "localhost | ",
            "a b.org | ",
            "x@ | "})
    void adminPatternsAreDomainsOrAddressesLowerCased(String raw, String expected) {
        assertThat(EmailBlocklistService.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void duplicatesAreRejected() {
        EmailBlocklistRepository repository = mock(EmailBlocklistRepository.class);
        EmailBlocklistEntry existing = entry("spam.example");
        existing.setId(java.util.UUID.randomUUID());
        when(repository.findByPatternIgnoreCase("spam.example")).thenReturn(java.util.Optional.of(existing));
        EmailBlocklistService service = new EmailBlocklistService(repository);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.add("SPAM.example", null))
                .isInstanceOfSatisfying(network.foodsharing.common.error.ApiException.class, ex -> assertThat(
                        ex.getCode()).isEqualTo(network.foodsharing.api.model.ApiErrorCode.BLOCKLIST_DUPLICATE));
    }

    private static EmailBlocklistEntry entry(String pattern) {
        EmailBlocklistEntry entry = new EmailBlocklistEntry();
        entry.setPattern(pattern);
        return entry;
    }
}
