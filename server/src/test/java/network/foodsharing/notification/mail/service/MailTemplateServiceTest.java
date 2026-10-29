package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MessageSourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MailTemplateServiceTest {

    private final MailTemplateService service = service();

    @ParameterizedTest
    @EnumSource(Language.class)
    void magicLinkRendersInEveryLanguage(Language language) {
        RenderedMail mail = service.render("magic-link", language, variables("Lea"));

        assertThat(mail.language()).isEqualTo(language);
        assertThat(mail.subject()).isNotBlank().doesNotStartWith("mail.");
        assertThat(mail.textBody()).contains("https://foodsharing.test/login/verify?token=abc")
                .contains("30").contains("Lea").doesNotContain("[(").doesNotContain("th:text").doesNotContain("th:href");
        assertThat(mail.htmlBody()).contains("href=\"https://foodsharing.test/login/verify?token=abc\"")
                .contains("Lea").doesNotContain("th:text").doesNotContain("th:href");
    }

    @Test
    void germanIsTheDefaultAndNamesAreOptional() {
        RenderedMail mail = service.render("magic-link", null, variables(null));
        assertThat(mail.language()).isEqualTo(Language.DE);
        assertThat(mail.subject()).isEqualTo("Dein Anmeldelink für foodsharing");
        assertThat(mail.textBody()).startsWith("Hallo,");
    }

    @Test
    void htmlVariablesAreEscaped() {
        RenderedMail mail = service.render("magic-link", Language.EN, variables("<b>Eve</b>"));
        assertThat(mail.htmlBody()).contains("&lt;b&gt;Eve&lt;/b&gt;").doesNotContain("<b>Eve</b>");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void regionSenderAddsAFooterLineInEveryLanguage(Language language) {
        Map<String, Object> variables = variables("Lea");
        variables.put("senderRegion", "Stadt Zürich");
        RenderedMail mail = service.render("magic-link", language, variables);
        String expected = service.senderLine(language, variables);
        assertThat(expected).contains("foodsharing Stadt Zürich").doesNotContain("{0}").doesNotContain("mail.footer");
        assertThat(mail.htmlBody()).contains("data-sender-region=\"true\"").contains(expected);
        assertThat(mail.textBody().stripTrailing()).endsWith("-- \n" + expected);
    }

    @Test
    void withoutRegionSenderThereIsNoSenderLine() {
        RenderedMail mail = service.render("magic-link", Language.DE, variables("Lea"));
        assertThat(mail.htmlBody()).doesNotContain("data-sender-region").doesNotContain("Im Auftrag");
        assertThat(mail.textBody()).doesNotContain("-- \n");
        Map<String, Object> blank = variables("Lea");
        blank.put("senderRegion", " ");
        assertThat(service.senderLine(Language.DE, blank)).isNull();
        assertThat(service.senderLine(Language.DE, Map.of("senderRegion", "Bern")))
                .isEqualTo("Gesendet im Auftrag von foodsharing Bern.");
        assertThat(service.senderLine(Language.EN, Map.of("senderRegion", "Bern")))
                .isEqualTo("Sent on behalf of foodsharing Bern.");
    }

    @Test
    void unknownTemplatesFail() {
        assertThatThrownBy(() -> service.render("nope", Language.DE, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<String, Object> variables(String displayName) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("link", "https://foodsharing.test/login/verify?token=abc");
        variables.put("validityMinutes", 30);
        variables.put("displayName", displayName);
        return variables;
    }

    private static MailTemplateService service() {
        try {
            AppProperties properties = new AppProperties();
            properties.setBaseUrl("https://foodsharing.test");
            return new MailTemplateService(new MessageSourceConfig().messageSource(), properties);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
