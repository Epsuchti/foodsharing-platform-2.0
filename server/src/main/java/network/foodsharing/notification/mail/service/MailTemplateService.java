package network.foodsharing.notification.mail.service;

import java.util.Locale;
import java.util.Map;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders mail templates with Thymeleaf: {@code templates/mail/<template>_<lang>.html} (HTML mode) and
 * {@code .txt} (TEXT mode, {@code [(${var})]} syntax, required); the subject is the static message
 * {@code mail.<template>.subject} from {@code i18n/<module>/messages_<lang>.properties}. Missing language variants
 * fall back to {@code de}. Variables {@code baseUrl} and {@code language} are always set.
 */
@Service
public class MailTemplateService {

    static final String PREFIX = "templates/mail/";

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;
    private final MessageSource messageSource;
    private final AppProperties properties;

    public MailTemplateService(MessageSource messageSource, AppProperties properties) {
        this.messageSource = messageSource;
        this.properties = properties;
        this.htmlEngine = engine(TemplateMode.HTML, ".html");
        this.textEngine = engine(TemplateMode.TEXT, ".txt");
    }

    public RenderedMail render(String template, Language requested, Map<String, Object> variables) {
        Language language = resolveLanguage(template, requested == null ? Language.DE : requested);
        Context context = new Context(locale(language));
        context.setVariables(variables);
        context.setVariable("baseUrl", properties.getBaseUrl());
        context.setVariable("language", language.getValue());
        String name = template + "_" + language.getValue();
        String text = textEngine.process(name, context);
        String html = exists(name + ".html") ? htmlEngine.process(name, context) : null;
        return new RenderedMail(interpolate(subject(template, language), variables), text, html, language);
    }

    /** Replaces {@code {name}} placeholders of a subject with the template variables (missing ones stay). */
    static String interpolate(String subject, Map<String, Object> variables) {
        if (subject == null || variables == null || subject.indexOf('{') < 0) {
            return subject;
        }
        String result = subject;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private String subject(String template, Language language) {
        String key = "mail." + template + ".subject";
        try {
            return messageSource.getMessage(key, null, locale(language));
        } catch (NoSuchMessageException ex) {
            return messageSource.getMessage(key, null, key, Locale.GERMAN);
        }
    }

    private static Language resolveLanguage(String template, Language requested) {
        if (exists(template + "_" + requested.getValue() + ".txt")) {
            return requested;
        }
        if (exists(template + "_" + Language.DE.getValue() + ".txt")) {
            return Language.DE;
        }
        throw new IllegalArgumentException("Unknown mail template " + template);
    }

    static Locale locale(Language language) {
        return Locale.of(language.getValue());
    }

    private static boolean exists(String file) {
        return new ClassPathResource(PREFIX + file).exists();
    }

    private static TemplateEngine engine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(PREFIX);
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);
        // SpringTemplateEngine: SpEL expressions (plain Thymeleaf would need OGNL, which is not on the classpath).
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
