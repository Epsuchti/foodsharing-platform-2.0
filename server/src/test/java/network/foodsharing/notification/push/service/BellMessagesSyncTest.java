package network.foodsharing.notification.push.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** A23: the server copy of the bell titles (push/Telegram) equals the client catalog (scripts/sync-bell-messages.mjs). */
class BellMessagesSyncTest {

    @ParameterizedTest
    @ValueSource(strings = {"de", "en", "gws", "fr", "it"})
    @SuppressWarnings("unchecked")
    void serverBellMessagesMatchTheClientCatalog(String lang) throws IOException {
        Map<String, String> client = new TreeMap<>(JsonMapper.builder().build().readValue(
                Path.of("../client/src/app/features/bells/i18n/" + lang + ".json").toFile(), Map.class));
        client.keySet().removeIf(key -> !key.startsWith("bell."));
        Properties server = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/main/resources/i18n/bells/messages_" + lang + ".properties"), StandardCharsets.UTF_8)) {
            server.load(reader);
        }
        assertThat(new TreeMap<>(server)).as("run node scripts/sync-bell-messages.mjs").isEqualTo(client);
    }
}
