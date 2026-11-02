package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.service.HistoryActionRegistry;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Cross-checks the contract with the code: every {@code x-history} names an existing action enum constant
 * ({@code <EnumSimpleName>.<CONSTANT>} or NONE); every operation has x-permission and an operationId.
 */
class OpenApiContractTest {

    private static final Path PATHS = Path.of("src/main/resources/openapi/paths");
    private static final Set<String> MUTATING = Set.of("post", "put", "patch", "delete");
    private static final Set<String> STORE_IDENTITY = Set.of("businessName", "storeName", "storeId", "StoreRef");

    @Test
    void xHistoryReferencesExistingActions() throws IOException {
        Set<String> known = HistoryActionRegistry.scan(HistoryActionRegistry.BASE_PACKAGE).stream()
                .map(action -> action.getClass().getSimpleName() + "." + ((Enum<?>) action).name())
                .collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> operation : operations().entrySet()) {
            Object history = operation.getValue().get("x-history");
            String method = operation.getKey().substring(0, operation.getKey().indexOf(' '));
            if (MUTATING.contains(method) && history == null) {
                problems.add(operation.getKey() + ": mutating operation without x-history");
            }
            if (history != null && !"NONE".equals(history) && !known.contains(String.valueOf(history))) {
                problems.add(operation.getKey() + ": unknown x-history " + history);
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void everyOperationDocumentsPermissionAndOperationId() throws IOException {
        List<String> problems = new ArrayList<>();
        operations().forEach((name, operation) -> {
            if (operation.get("x-permission") == null) {
                problems.add(name + ": missing x-permission");
            }
            if (operation.get("operationId") == null) {
                problems.add(name + ": missing operationId");
            }
        });
        assertThat(problems).isEmpty();
        assertThat(operations()).isNotEmpty();
    }

    @Test
    void historyActionsHaveUniqueCodesPerSubjectType() {
        List<HistoryAction> actions = HistoryActionRegistry.scan(HistoryActionRegistry.BASE_PACKAGE);
        long distinct = actions.stream().map(action -> action.subjectType() + ":" + action.code()).distinct().count();
        assertThat(distinct).isEqualTo(actions.size());
    }

    /** Decision 156 (S17): nothing reachable without a login can carry a store's identity. */
    @Test
    void schemasReachableWithoutLoginHoldNoStoreIdentity() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> operation : operations().entrySet()) {
            boolean open = operation.getKey().contains(" /api/public/") || (operation.getValue().get("security")
                    instanceof List<?> security && security.isEmpty());
            if (!open) {
                continue;
            }
            Set<String> seen = new java.util.HashSet<>();
            collect(operation.getValue().get("responses"), PATHS, seen, problems, operation.getKey());
        }
        assertThat(problems).isEmpty();
    }

    /** Walks a parsed YAML value: property names are checked and every $ref is followed. */
    @SuppressWarnings("unchecked")
    private static void collect(Object node, Path base, Set<String> seen, List<String> problems, String where)
            throws IOException {
        if (node instanceof List<?> list) {
            for (Object item : list) {
                collect(item, base, seen, problems, where);
            }
        } else if (node instanceof Map<?, ?> map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                if (STORE_IDENTITY.contains(entry.getKey())) {
                    problems.add(where + ": " + entry.getKey());
                }
                if ("$ref".equals(entry.getKey())) {
                    String ref = String.valueOf(entry.getValue());
                    int hash = ref.indexOf('#');
                    Path file = base.resolve(ref.substring(0, hash)).normalize();
                    String key = ref.substring(hash + 2);
                    if (STORE_IDENTITY.contains(key)) {
                        problems.add(where + ": " + key);
                    }
                    if (seen.add(file + "#" + key)) {
                        try (InputStream in = Files.newInputStream(file)) {
                            Map<String, Object> schemas = new Yaml().load(in);
                            collect(schemas.get(key), file.getParent(), seen, problems, where);
                        }
                    }
                } else {
                    collect(entry.getValue(), base, seen, problems, where);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> operations() throws IOException {
        Map<String, Map<String, Object>> result = new java.util.TreeMap<>();
        try (Stream<Path> files = Files.list(PATHS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yaml")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    Object loaded = new Yaml().load(in);
                    if (!(loaded instanceof Map<?, ?> paths)) {
                        continue;
                    }
                    paths.forEach((path, item) -> ((Map<String, Object>) item).forEach((method, operation) -> {
                        if (operation instanceof Map<?, ?> op) {
                            result.put(method + " " + path, (Map<String, Object>) op);
                        }
                    }));
                }
            }
        }
        return result;
    }
}
