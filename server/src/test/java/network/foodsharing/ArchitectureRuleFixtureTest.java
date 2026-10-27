package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/** Proves rule 4 catches a mutating service method that records no history (fixtures in {@code archfixture}). */
class ArchitectureRuleFixtureTest {

    private final JavaClasses fixtures = new ClassFileImporter()
            .importPackages("archfixture", "network.foodsharing.history");

    @Test
    void rule4ReportsExactlyTheMethodsWithoutHistory() {
        EvaluationResult result = ArchitectureRules.MUTATING_SERVICES_RECORD_HISTORY.evaluate(fixtures);

        assertThat(result.hasViolation()).isTrue();
        String report = String.join("\n", result.getFailureReport().getDetails());
        assertThat(report)
                .contains("archfixture.service.MissingHistoryService.change()")
                .contains("archfixture.service.ClassLevelTransactionalService.update()")
                .doesNotContain("RecordingService.change()")
                .doesNotContain("DelegatingService.change()")
                .doesNotContain("LambdaRecordingService.change()")
                .doesNotContain("ExemptService.touch()")
                .doesNotContain("ReadOnlyService")
                .doesNotContain("ClassLevelTransactionalService.read()");
        assertThat(result.getFailureReport().getDetails()).hasSize(2);
    }

    @Test
    void rule4FailsTheBuildForAViolation() {
        assertThatThrownBy(() -> ArchitectureRules.MUTATING_SERVICES_RECORD_HISTORY.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("MissingHistoryService.change()");
    }
}
