package network.foodsharing.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import network.foodsharing.common.csv.CsvParser;
import network.foodsharing.quiz.domain.QuizKind;
import org.junit.jupiter.api.Test;

/**
 * Q1: every quiz is named in all five languages. The names are data of the quiz module (seed {@code quiz_i18n.csv}),
 * not i18n keys, so the client catalog check cannot see a missing one. The EinAb guide quiz is listed only for its
 * editors, so no journey reads its names: they are checked here with the others.
 */
class QuizSeedTextsTest {

    static final List<String> LANGUAGES = List.of("de", "en", "gws", "fr", "it");
    /** The EINAB_GUIDE quiz is production data (changelog 190), its texts come with the seed. */
    static final String EINAB_GUIDE_ID = "00000013-0001-4000-8000-000000000008";

    private static List<Map<String, String>> rows(String path) throws IOException {
        try (InputStream in = QuizSeedTextsTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            List<List<String>> lines = CsvParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<String> header = lines.getFirst();
            return lines.subList(1, lines.size()).stream().filter(line -> line.size() == header.size()).map(line -> {
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.size(); i++) {
                    row.put(header.get(i), line.get(i));
                }
                return row;
            }).toList();
        }
    }

    @Test
    void everyQuizIsNamedInFiveLanguages() throws IOException {
        Map<String, String> kindById = new HashMap<>();
        for (Map<String, String> quiz : rows("/db/seed/quiz/quiz.csv")) {
            kindById.put(quiz.get("id"), quiz.get("kind"));
        }
        kindById.put(EINAB_GUIDE_ID, QuizKind.EINAB_GUIDE.name());
        assertThat(kindById.values()).containsExactlyInAnyOrder(
                java.util.Arrays.stream(QuizKind.values()).map(Enum::name).toArray(String[]::new));

        Map<String, Map<String, String>> names = new TreeMap<>();
        for (Map<String, String> text : rows("/db/seed/quiz/quiz_i18n.csv")) {
            String kind = kindById.get(text.get("owner_id"));
            assertThat(kind).as("quiz of %s", text.get("id")).isNotNull();
            assertThat(names.computeIfAbsent(kind, k -> new TreeMap<>()).put(text.get("language"), text.get("name")))
                    .as("%s %s twice", kind, text.get("language")).isNull();
        }
        assertThat(names).hasSize(QuizKind.values().length);
        names.forEach((kind, byLanguage) -> {
            assertThat(byLanguage.keySet()).as(kind).containsExactlyInAnyOrderElementsOf(LANGUAGES);
            byLanguage.forEach((language, name) -> assertThat(name).as("%s %s", kind, language).isNotBlank());
        });
        assertThat(names.get("EINAB_GUIDE")).containsEntry("de", "EinAb-Begleitungs-Quiz")
                .containsEntry("en", "EinAb guide quiz").containsEntry("gws", "EinAb-Begleitigs-Quiz")
                .containsEntry("fr", "Quiz accompagnateur·rice EinAb").containsEntry("it", "Quiz accompagnatore EinAb");
    }
}
