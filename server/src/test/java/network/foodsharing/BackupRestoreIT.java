package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * D19.2: the backup procedure of {@code server/deploy/backup.sh} / {@code restore.sh} (pg_dump custom format,
 * {@code --no-owner --no-privileges}, pg_restore {@code --exit-on-error}) round-trips the full schema with the seed
 * data from container A into an empty container B: same tables, same row counts ({@code deploy/lib/table-counts.sql}),
 * same content checksum per table, same sequence positions.
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema,seed", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class BackupRestoreIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer SOURCE = new PostgreSQLContainer("postgres:17");

    @Container
    static final PostgreSQLContainer TARGET = new PostgreSQLContainer("postgres:17");

    private static final Path COUNTS_SQL = Path.of("deploy", "lib", "table-counts.sql");

    /** md5 over every row of every public table, in a stable order. */
    private static final String CHECKSUMS_SQL = """
            SELECT table_name || '|' || (xpath('/row/c/text()', query_to_xml(format(
                'SELECT md5(coalesce(string_agg(t::text, E''\\n'' ORDER BY t::text), '''')) AS c FROM %I.%I t',
                table_schema, table_name), false, true, '')))[1]::text
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            ORDER BY table_name;
            SELECT sequence_name || '|' || (xpath('/row/c/text()', query_to_xml(format(
                'SELECT last_value AS c FROM %I.%I', sequence_schema, sequence_name), false, true, '')))[1]::text
            FROM information_schema.sequences WHERE sequence_schema = 'public' ORDER BY sequence_name;
            """;

    @TempDir
    Path tmp;

    @Test
    void dumpOfAFullDatabaseRestoresIdenticallyIntoAnEmptyOne() throws Exception {
        ExecResult dump = SOURCE.execInContainer("pg_dump", "-U", SOURCE.getUsername(), "-d", SOURCE.getDatabaseName(),
                "--format=custom", "--no-owner", "--no-privileges", "-f", "/tmp/backup.dump");
        assertThat(dump.getExitCode()).as(dump.getStderr()).isZero();
        Path local = tmp.resolve("backup.dump");
        SOURCE.copyFileFromContainer("/tmp/backup.dump", local.toString());
        assertThat(Files.size(local)).isGreaterThan(10_000);

        TARGET.copyFileToContainer(MountableFile.forHostPath(local), "/tmp/backup.dump");
        ExecResult restore = TARGET.execInContainer("pg_restore", "-U", TARGET.getUsername(), "-d",
                TARGET.getDatabaseName(), "--no-owner", "--no-privileges", "--exit-on-error", "/tmp/backup.dump");
        assertThat(restore.getExitCode()).as(restore.getStderr()).isZero();

        String countsSql = Files.readString(COUNTS_SQL, StandardCharsets.UTF_8);
        List<String> sourceCounts = psql(SOURCE, countsSql);
        List<String> targetCounts = psql(TARGET, countsSql);
        assertThat(sourceCounts).hasSizeGreaterThan(100).contains("databasechangeloglock|1");
        assertThat(targetCounts).isEqualTo(sourceCounts);
        long rows = sourceCounts.stream().mapToLong(l -> Long.parseLong(l.substring(l.indexOf('|') + 1))).sum();
        assertThat(rows).isGreaterThan(1_000);
        assertThat(sourceCounts.stream().filter(l -> l.startsWith("user_account|")).findFirst().orElseThrow())
                .isNotEqualTo("user_account|0");

        assertThat(psql(TARGET, CHECKSUMS_SQL)).isEqualTo(psql(SOURCE, CHECKSUMS_SQL));
    }

    private static List<String> psql(PostgreSQLContainer container, String sql) throws Exception {
        container.copyFileToContainer(Transferable.of(sql), "/tmp/query.sql");
        ExecResult result = container.execInContainer("psql", "-U", container.getUsername(), "-d",
                container.getDatabaseName(), "-At", "-v", "ON_ERROR_STOP=1", "-f", "/tmp/query.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return Arrays.stream(result.getStdout().split("\\R")).filter(l -> l.contains("|")).toList();
    }
}
