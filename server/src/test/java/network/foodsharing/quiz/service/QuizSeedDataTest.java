package network.foodsharing.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import network.foodsharing.common.csv.CsvParser;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Q1/C1 quiz data as Liquibase loads it: every question (seed and the production foodsaver pool of changelog 197)
 * has a correct answer; seed questions and answers are in the five languages; the pool holds 64 German questions,
 * each linked to a learning chapter; the seed's demo foodsaver questions are retired, so dev and e2e quiz the pool.
 */
class QuizSeedDataTest {

    static final String FOODSAVER_QUIZ = "00000013-0001-4000-8000-000000000001";
    static final String POOL = "/db/changelog/changes/197-quiz-foodsaver-pool.yaml";

    private static String read(String path) throws IOException {
        try (InputStream in = QuizSeedDataTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<Map<String, String>> csv(String file) throws IOException {
        List<List<String>> lines = CsvParser.parse(read("/db/seed/quiz/" + file));
        List<String> header = lines.getFirst();
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<String> line : lines.subList(1, lines.size())) {
            if (line.size() != header.size()) {
                continue;
            }
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), "NULL".equals(line.get(i)) ? null : line.get(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /** The rows the pool changelog inserts into {@code table}, column name → value (or valueComputed). */
    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> pool(String table) throws IOException {
        Map<String, Object> root = new Yaml().load(read(POOL));
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, Object> entry : (List<Map<String, Object>>) root.get("databaseChangeLog")) {
            Map<String, Object> changeSet = (Map<String, Object>) entry.get("changeSet");
            for (Map<String, Object> change : (List<Map<String, Object>>) changeSet.getOrDefault("changes", List.of())) {
                Map<String, Object> insert = (Map<String, Object>) change.get("insert");
                if (insert == null || !table.equals(insert.get("tableName"))) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (Map<String, Object> column : (List<Map<String, Object>>) insert.get("columns")) {
                    Map<String, Object> c = (Map<String, Object>) column.get("column");
                    Object value = c.containsKey("value") ? c.get("value") : c.get("valueComputed");
                    row.put((String) c.get("name"), value == null ? null : String.valueOf(value));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    @Test
    void everyQuestionHasACorrectAnswer() throws IOException {
        Set<String> questions = new HashSet<>();
        csv("quiz_question.csv").forEach(row -> questions.add(row.get("id")));
        pool("quiz_question").forEach(row -> questions.add(row.get("id")));
        Set<String> withCorrect = new HashSet<>();
        csv("quiz_answer.csv").stream().filter(row -> "CORRECT".equals(row.get("rating")))
                .forEach(row -> withCorrect.add(row.get("question_id")));
        pool("quiz_answer").stream().filter(row -> "CORRECT".equals(row.get("rating")))
                .forEach(row -> withCorrect.add(row.get("question_id")));
        assertThat(questions).isNotEmpty();
        assertThat(withCorrect).containsAll(questions);
    }

    @Test
    void seedQuestionsAndAnswersAreInFiveLanguages() throws IOException {
        Map<String, Long> questionTexts = csv("quiz_question_i18n.csv").stream()
                .collect(Collectors.groupingBy(row -> row.get("owner_id"), Collectors.counting()));
        for (Map<String, String> question : csv("quiz_question.csv")) {
            if (question.get("created_by") == null) {
                assertThat(questionTexts.get(question.get("id"))).as(question.get("id")).isEqualTo(5L);
            }
        }
        Map<String, Long> answerTexts = csv("quiz_answer_i18n.csv").stream()
                .collect(Collectors.groupingBy(row -> row.get("owner_id"), Collectors.counting()));
        for (Map<String, String> answer : csv("quiz_answer.csv")) {
            if (answer.get("created_by") == null) {
                assertThat(answerTexts.get(answer.get("id"))).as(answer.get("id")).isEqualTo(5L);
            }
        }
    }

    @Test
    void thePoolHas64GermanQuestionsLinkedToTheLearningChaptersAndTheDemoQuestionsAreRetired() throws IOException {
        List<Map<String, String>> pool = pool("quiz_question");
        assertThat(pool).hasSize(64).allSatisfy(row -> {
            assertThat(row.get("quiz_id")).contains("kind = 'FOODSAVER'");
            assertThat(row.get("wiki_link")).startsWith("/learn/");
        });
        Set<String> german = pool("quiz_question_i18n").stream().filter(row -> "de".equals(row.get("language")))
                .map(row -> row.get("owner_id")).collect(Collectors.toSet());
        assertThat(german).containsAll(pool.stream().map(row -> row.get("id")).toList());

        assertThat(csv("quiz_question.csv")).filteredOn(row -> FOODSAVER_QUIZ.equals(row.get("quiz_id")))
                .isNotEmpty().allSatisfy(row -> assertThat(row.get("deleted_at")).as(row.get("id")).isNotNull());
    }
}
