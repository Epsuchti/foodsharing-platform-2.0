package network.foodsharing.history;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import network.foodsharing.history.service.HistoryActionRegistry;
import org.junit.jupiter.api.Test;

/**
 * History texts in the UI (decision row amending 54, item 3): every {@link HistoryAction} has a German label
 * {@code history.action.<SUBJECT>.<CODE>} and every metadata key the server writes has a label
 * {@code shared.historyTimeline.meta.<key>}, so the timeline never shows a raw code. The other four languages follow
 * from {@code npm run i18n:check} (same keys in every language).
 *
 * <p>Metadata keys are read from the sources: {@code .metadata("key", …)}, {@code .metadata(Map.of("key", …))},
 * {@code Map.of} returned by a method named {@code metadata}, and {@code var.put("key", …)} on a map passed as
 * {@code .metadata(var)}. Pure id keys ({@code id}, {@code *Id}, {@code *Ids}) are hidden in the UI and need no label;
 * keys computed at runtime (notification categories) are humanized there.
 */
class HistoryI18nCoverageTest {

    static final Path SOURCES = Path.of("src", "main", "java");
    static final Path HISTORY_DE = Path.of("..", "client", "src", "app", "features", "history", "i18n", "de.json");
    static final Path SHARED_DE = Path.of("..", "client", "src", "app", "shared", "i18n", "de.json");

    static final Pattern ID_KEY = Pattern.compile("^(id|.*Id|.*Ids)$");
    private static final Pattern LITERAL = Pattern.compile("\\.metadata\\(\\s*\"([A-Za-z]+)\"");
    private static final Pattern VARIABLE = Pattern.compile("\\.metadata\\((\\w+)\\)");
    private static final Pattern MAP_OF = Pattern.compile("Map\\.of\\(");
    private static final Pattern DIRECT = Pattern.compile("metadata\\(\\s*$");
    private static final Pattern RETURN = Pattern.compile("return\\s*$");
    private static final Pattern METHOD = Pattern.compile("\\n\\s*[\\w<>,? .]+\\s(\\w+)\\([^;{}]*\\)\\s*\\{");
    private static final Pattern KEY = Pattern.compile("\"([A-Za-z]+)\"");

    @Test
    void everyHistoryActionHasAGermanLabel() throws IOException {
        Map<String, Object> de = catalog(HISTORY_DE);
        List<String> missing = new HistoryActionRegistry().all().stream()
                .map(a -> HistoryActionRegistry.labelKey(a.subjectType(), a.code()))
                .filter(key -> !de.containsKey(key)).toList();
        assertThat(missing).as("labels missing in %s", HISTORY_DE).isEmpty();
    }

    @Test
    void everyMetadataKeyHasAGermanLabel() throws IOException {
        Map<String, Object> de = catalog(SHARED_DE);
        List<String> missing = metadataKeys(sources()).stream()
                .filter(key -> !ID_KEY.matcher(key).matches())
                .filter(key -> !de.containsKey("shared.historyTimeline.meta." + key)).toList();
        assertThat(missing).as("shared.historyTimeline.meta.<key> missing in %s", SHARED_DE).isEmpty();
    }

    @Test
    void readsTheMetadataFormsTheServerUses() {
        String source = """
                class A {
                    void a() {
                        history.record(event.metadata("pickupAt", at).metadata(
                                "slotCount", 2).build());
                        history.record(event.metadata(Map.of("previousCount", 1, "newCount", f("x", "y"))).build());
                        Map<String, Object> changed = new LinkedHashMap<>();
                        changed.put("day", today);
                        other.put("notMetadata", 1);
                        history.record(event.metadata(changed).build());
                        mail.send(Map.of("subject", "x"));
                    }

                    private static Map<String, Object> metadata(Block b) {
                        return Map.of("scope", b.scope(), "until", "never");
                    }
                }
                """;
        assertThat(metadataKeys(Map.of(Path.of("A.java"), source)))
                .containsExactly("day", "newCount", "pickupAt", "previousCount", "scope", "slotCount", "until");
    }

    /** Metadata keys of all given sources (sorted). */
    static Set<String> metadataKeys(Map<Path, String> sources) {
        Set<String> keys = new TreeSet<>();
        for (String text : sources.values()) {
            if (!text.contains(".metadata(")) {
                continue;
            }
            Matcher literal = LITERAL.matcher(text);
            while (literal.find()) {
                keys.add(literal.group(1));
            }
            Matcher variable = VARIABLE.matcher(text);
            while (variable.find()) {
                Matcher put = Pattern.compile("\\b" + Pattern.quote(variable.group(1)) + "\\.put\\(\\s*\"([A-Za-z]+)\"")
                        .matcher(text);
                while (put.find()) {
                    keys.add(put.group(1));
                }
            }
            Matcher mapOf = MAP_OF.matcher(text);
            while (mapOf.find()) {
                String before = text.substring(0, mapOf.start());
                if (DIRECT.matcher(before).find() || RETURN.matcher(before).find() && "metadata".equals(method(before))) {
                    List<String> args = arguments(text, mapOf.end());
                    for (int i = 0; i < args.size(); i += 2) {
                        Matcher key = KEY.matcher(args.get(i));
                        if (key.matches()) {
                            keys.add(key.group(1));
                        }
                    }
                }
            }
        }
        return keys;
    }

    /** Name of the last method declared before the end of {@code before}. */
    private static String method(String before) {
        Matcher m = METHOD.matcher(before);
        String name = null;
        while (m.find()) {
            name = m.group(1);
        }
        return name;
    }

    /** Top-level arguments of the call whose argument list starts at {@code start} (after the opening parenthesis). */
    static List<String> arguments(String text, int start) {
        List<String> args = new ArrayList<>();
        StringBuilder arg = new StringBuilder();
        int depth = 0;
        boolean string = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                arg.append(c);
                if (c == '\\') {
                    arg.append(text.charAt(++i));
                } else if (c == '"') {
                    string = false;
                }
            } else if (c == '"') {
                string = true;
                arg.append(c);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
                arg.append(c);
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    args.add(arg.toString().strip());
                    return args;
                }
                depth--;
                arg.append(c);
            } else if (c == ',' && depth == 0) {
                args.add(arg.toString().strip());
                arg.setLength(0);
            } else {
                arg.append(c);
            }
        }
        return args;
    }

    private static Map<Path, String> sources() throws IOException {
        Map<Path, String> result = new java.util.TreeMap<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                result.put(file, Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> catalog(Path file) throws IOException {
        return JsonMapper.builder().build().readValue(file.toFile(), Map.class);
    }
}
