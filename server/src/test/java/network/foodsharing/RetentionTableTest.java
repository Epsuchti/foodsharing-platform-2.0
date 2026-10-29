package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import network.foodsharing.common.jobs.CleanupTask;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.service.HistoryActionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * D17.3: {@code docs/privacy/retention.md} lists every {@link HistoryAction} with its {@link RetentionPolicy}, explains
 * every policy, and names every {@link CleanupTask}; every remover it names exists and is a cleanup task or job.
 * On a mismatch the expected action table is written to {@code target/privacy/retention-actions.md}.
 */
class RetentionTableTest {

    static final Path FILE = Path.of("..", "docs", "privacy", "retention.md");

    private static final Pattern ACTION_ROW = Pattern.compile("^\\| `([A-Z_]+)` \\| `([A-Z0-9_]+)` \\| ([A-Z0-9_]+) \\|");
    private static final Pattern CLASS = Pattern.compile("`([A-Z][A-Za-z0-9]+)`");

    @Test
    void everyHistoryActionIsListedWithItsPolicy() throws IOException {
        List<String> expected = new ArrayList<>();
        for (HistoryAction action : new HistoryActionRegistry().all()) {
            expected.add("| `" + action.subjectType() + "` | `" + action.code() + "` | " + action.retention() + " |");
        }
        List<String> documented = new ArrayList<>();
        for (String line : section("History actions")) {
            Matcher row = ACTION_ROW.matcher(line);
            if (row.find()) {
                documented.add("| `" + row.group(1) + "` | `" + row.group(2) + "` | " + row.group(3) + " |");
            }
        }
        if (!documented.equals(expected)) {
            Files.createDirectories(Path.of("target", "privacy"));
            Files.write(Path.of("target", "privacy", "retention-actions.md"), expected);
        }
        assertThat(documented).as("%s section 'History actions' (expected rows in target/privacy/retention-actions.md)",
                FILE).containsExactlyElementsOf(expected);
    }

    @Test
    void everyPolicyIsExplained() throws IOException {
        String policies = String.join("\n", section("Policies"));
        for (RetentionPolicy policy : RetentionPolicy.values()) {
            assertThat(policies).as(policy.name()).contains("| " + policy.name() + " |");
        }
    }

    @Test
    void everyCleanupTaskIsListedAndEveryRemoverExists() throws Exception {
        Map<String, Class<?>> removers = new TreeMap<>();
        for (String line : section("Personal data outside the history")) {
            if (!line.startsWith("| `")) {
                continue;
            }
            String[] cells = line.split("\\|");
            Matcher name = CLASS.matcher(cells[cells.length - 1]);
            while (name.find()) {
                removers.put(name.group(1), null);
            }
        }
        Set<String> tasks = new TreeSet<>();
        for (Class<?> type : implementations(CleanupTask.class)) {
            tasks.add(type.getSimpleName());
        }
        assertThat(removers.keySet()).as("cleanup tasks named in %s", FILE).containsAll(tasks);
        Set<String> known = new TreeSet<>(tasks);
        implementations(RunnableJob.class).forEach(type -> known.add(type.getSimpleName()));
        assertThat(known).as("removers named in %s are cleanup tasks or jobs", FILE).containsAll(removers.keySet());
    }

    static List<String> section(String title) throws IOException {
        List<String> lines = Files.readAllLines(FILE);
        int start = lines.indexOf("## " + title);
        assertThat(start).as("section '## %s' in %s", title, FILE).isNotNegative();
        List<String> result = new ArrayList<>();
        for (String line : lines.subList(start + 1, lines.size())) {
            if (line.startsWith("## ")) {
                break;
            }
            result.add(line);
        }
        return result;
    }

    private static List<Class<?>> implementations(Class<?> type) throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isConcrete();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(type));
        List<Class<?>> result = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(HistoryActionRegistry.BASE_PACKAGE)) {
            Class<?> found = Class.forName(candidate.getBeanClassName());
            boolean production = !found.getProtectionDomain().getCodeSource().getLocation().toString()
                    .contains("test-classes");
            if (production && type.isAssignableFrom(found)) {
                result.add(found);
            }
        }
        return result;
    }
}
