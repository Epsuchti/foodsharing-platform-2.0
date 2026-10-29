package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.csv.CsvParser;
import org.junit.jupiter.api.Test;

/**
 * D16.1: the Zürich regions file ({@code docs/data/zuerich/regions.csv}) is a superset of the seeded Zürich tree, so
 * dev/e2e (seed) and production (file) share slugs, parents and types for every region from Europa down to Zürich.
 */
class ZuerichRegionsFileTest {

    static final Path FILE = Path.of("..", "docs", "data", "zuerich", "regions.csv");

    record Entry(String parentSlug, String type) {
    }

    @Test
    void everySeededZuerichRegionAppearsWithSameParentAndType() throws IOException {
        Map<String, Entry> file = new HashMap<>();
        List<List<String>> lines = CsvParser.parse(Files.readString(FILE, StandardCharsets.UTF_8));
        assertThat(lines.getFirst().getFirst()).startsWith("# MODEL pending ambassador confirmation");
        for (List<String> line : lines) {
            if (!CsvParser.isComment(line) && !line.getFirst().equals("parent_slug")) {
                assertThat(file.put(line.get(1), new Entry(line.get(0), line.get(3)))).as(line.get(1)).isNull();
            }
        }

        Map<String, String> slugById = new HashMap<>();
        Map<String, String[]> seed = new HashMap<>();
        try (InputStream in = getClass().getResourceAsStream("/db/seed/region/region.csv")) {
            List<List<String>> rows = CsvParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<String> header = rows.getFirst();
            int id = header.indexOf("id");
            int parent = header.indexOf("parent_id");
            int slug = header.indexOf("slug");
            int type = header.indexOf("type");
            for (List<String> row : rows.subList(1, rows.size())) {
                if (!row.get(type).equals("WORKING_GROUP")) {
                    slugById.put(row.get(id), row.get(slug));
                    seed.put(row.get(slug), new String[] {row.get(parent), row.get(type)});
                }
            }
        }
        int checked = 0;
        for (Map.Entry<String, String[]> region : seed.entrySet()) {
            if (!inZuerichChain(region.getKey(), seed, slugById)) {
                continue;
            }
            String parentSlug = "NULL".equals(region.getValue()[0]) ? "" : slugById.get(region.getValue()[0]);
            assertThat(file).as(region.getKey()).containsEntry(region.getKey(),
                    new Entry(parentSlug, region.getValue()[1]));
            checked++;
        }
        // europa, schweiz, kanton-zuerich, zuerich-stadt, winterthur + the five seeded sub-regions
        assertThat(checked).isGreaterThanOrEqualTo(10);
    }

    /** Ancestors of kanton-zuerich, kanton-zuerich itself and its whole subtree. */
    private static boolean inZuerichChain(String slug, Map<String, String[]> seed, Map<String, String> slugById) {
        if (List.of("europa", "schweiz").contains(slug)) {
            return true;
        }
        String current = slug;
        while (current != null) {
            if (current.equals("kanton-zuerich")) {
                return true;
            }
            String[] entry = seed.get(current);
            current = entry == null ? null : slugById.get(entry[0]);
        }
        return false;
    }
}
