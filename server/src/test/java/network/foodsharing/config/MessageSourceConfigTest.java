package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

class MessageSourceConfigTest {

    @Test
    void discoversModuleCatalogs() throws Exception {
        MessageSource source = new MessageSourceConfig().messageSource();
        assertThat(source.getMessage("app.name", null, Locale.forLanguageTag("fr"))).isEqualTo("foodsharing");
    }
}
