package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * D19.1: every environment placeholder of the shipped configuration (base + prod profile) is documented in
 * {@code docs/secrets.md} and listed in {@code server/deploy/.env.example}; every variable of the template and of
 * the compose file is documented, and required prod variables (no default) are not commented out in the template.
 */
class ProdSecretsInventoryTest {

    private static final Path SECRETS = Path.of("..", "docs", "secrets.md");
    private static final Path ENV_EXAMPLE = Path.of("deploy", ".env.example");
    private static final Path COMPOSE = Path.of("deploy", "compose.yaml");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(:?)");
    private static final Pattern ENV_LINE = Pattern.compile("(?m)^(#\\s*)?([A-Z][A-Z0-9_]*)=");

    @Test
    void everyPlaceholderIsDocumentedAndTemplated() throws IOException {
        String secrets = Files.readString(SECRETS, StandardCharsets.UTF_8);
        String template = Files.readString(ENV_EXAMPLE, StandardCharsets.UTF_8);
        Set<String> vars = new TreeSet<>();
        vars.addAll(placeholders("application.properties", false));
        vars.addAll(placeholders("application-prod.properties", false));
        assertThat(vars).hasSizeGreaterThan(20);

        Set<String> templated = new TreeSet<>();
        Matcher m = ENV_LINE.matcher(template);
        while (m.find()) {
            templated.add(m.group(2));
        }
        for (String var : vars) {
            assertThat(secrets).as("docs/secrets.md row for " + var).contains("| `" + var + "` |");
            assertThat(templated).as(".env.example line for " + var).contains(var);
        }
        for (String var : templated) {
            assertThat(secrets).as("docs/secrets.md row for template variable " + var).contains("| `" + var + "` |");
        }
    }

    @Test
    void requiredProdVariablesAreActiveInTheTemplate() throws IOException {
        String template = Files.readString(ENV_EXAMPLE, StandardCharsets.UTF_8);
        for (String var : placeholders("application-prod.properties", true)) {
            assertThat(template).as("required " + var + " uncommented in .env.example")
                    .containsPattern("(?m)^" + var + "=");
        }
    }

    @Test
    void composeVariablesAreDocumented() throws IOException {
        String secrets = Files.readString(SECRETS, StandardCharsets.UTF_8);
        Matcher m = PLACEHOLDER.matcher(Files.readString(COMPOSE, StandardCharsets.UTF_8));
        int count = 0;
        while (m.find()) {
            count++;
            assertThat(secrets).as("docs/secrets.md row for compose variable " + m.group(1))
                    .contains("| `" + m.group(1) + "` |");
        }
        assertThat(count).isPositive();
    }

    /** Placeholders of a properties file; {@code requiredOnly} keeps those without a default value. */
    private static Set<String> placeholders(String resource, boolean requiredOnly) throws IOException {
        String text;
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> result = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            if (!requiredOnly || m.group(2).isEmpty()) {
                result.add(m.group(1));
            }
        }
        return result;
    }
}
