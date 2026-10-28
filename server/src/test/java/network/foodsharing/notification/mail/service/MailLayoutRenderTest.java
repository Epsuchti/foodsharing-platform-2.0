package network.foodsharing.notification.mail.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.StaticMessageSource;

/** A27: every mail's HTML part is the layout in the mail language; the unsubscribe link only for newsletters. */
class MailLayoutRenderTest {

    private final MailTemplateService templates = new MailTemplateService(messages(), properties());

    private static StaticMessageSource messages() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("mail.notification/INTEGRATION_DOWN.subject", Locale.GERMAN, "Dienst {integration}");
        return source;
    }

    private static AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://foodsharing.example");
        return properties;
    }

    private static Map<String, Object> variables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("displayName", "Mia");
        variables.put("integration", "TELEGRAM");
        variables.put("message", "timeout");
        variables.put("link", "https://foodsharing.example/admin/integrations");
        return variables;
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void notificationFragmentIsWrappedInTheLayout(Language language) {
        RenderedMail mail = templates.render("notification/INTEGRATION_DOWN", language, variables());
        assertThat(mail.language()).isEqualTo(language);
        assertThat(mail.htmlBody()).startsWith("<!DOCTYPE html>")
                .contains("lang=\"" + language.getValue() + "\"")
                .contains("<strong>TELEGRAM</strong>")
                .contains("https://foodsharing.example/settings/notifications")
                .doesNotContain("data-unsubscribe");
        assertThat(mail.textBody()).contains("Mia").contains("TELEGRAM").contains("timeout");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void newsletterCarriesTheUnsubscribeLink(Language language) {
        Map<String, Object> variables = variables();
        variables.put("unsubscribeUrl", "https://foodsharing.example/newsletter/unsubscribe/abc");
        RenderedMail mail = templates.render("notification/INTEGRATION_DOWN", language, variables);
        assertThat(mail.htmlBody()).contains("data-unsubscribe")
                .contains("https://foodsharing.example/newsletter/unsubscribe/abc");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void textOnlyTemplatesGetEscapedParagraphsInTheLayout(Language language) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("displayName", "<b>Mia</b>");
        variables.put("storeName", "Coop");
        variables.put("link", "https://foodsharing.example/stores/1");
        RenderedMail mail = templates.render("store/invitation", language, variables);
        assertThat(mail.htmlBody()).contains("<!DOCTYPE html>").contains("&lt;b&gt;Mia&lt;/b&gt;")
                .doesNotContain("data-unsubscribe");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void magicLinkKeepsItsOwnHtmlInsideTheLayout(Language language) {
        RenderedMail mail = templates.render("magic-link", language,
                Map.of("displayName", "Mia", "link", "https://foodsharing.example/login/x", "validityMinutes", 30));
        assertThat(mail.htmlBody()).startsWith("<!DOCTYPE html>").contains("https://foodsharing.example/login/x")
                .containsOnlyOnce("<html");
    }

    @org.junit.jupiter.api.Test
    void subjectInterpolatesVariables() {
        assertThat(templates.render("notification/INTEGRATION_DOWN", Language.DE, variables()).subject())
                .isEqualTo("Dienst TELEGRAM");
    }
}
