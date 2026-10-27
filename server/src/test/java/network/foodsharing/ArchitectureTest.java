package network.foodsharing;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** docs/conventions.md A4 on the production classes (incl. generated code). */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .withImportOption(new ImportOption.DoNotIncludeJars())
                .importPackages(ArchitectureRules.ROOT);
    }

    @Test
    void rule1ControllersImplementExactlyOneGeneratedApi() {
        ArchitectureRules.CONTROLLERS_IMPLEMENT_ONE_GENERATED_API.check(classes);
    }

    @Test
    void rule2WebLayerDoesNotUseRepositoriesOrJpa() {
        ArchitectureRules.WEB_DOES_NOT_ACCESS_REPOSITORIES.check(classes);
        ArchitectureRules.WEB_DOES_NOT_USE_JPA.check(classes);
    }

    @Test
    void rule3GeneratedModelsOnlyAtTheEdge() {
        ArchitectureRules.GENERATED_MODELS_ONLY_AT_THE_EDGE.check(classes);
    }

    @Test
    void rule4MutatingServicesRecordHistory() {
        ArchitectureRules.MUTATING_SERVICES_RECORD_HISTORY.check(classes);
    }

    @Test
    void rule5OnlyClockConfigReadsTheSystemClock() {
        ArchitectureRules.NO_SYSTEM_CLOCK.check(classes);
    }

    @Test
    void rule6DevClassesOnlyInDevProfiles() {
        ArchitectureRules.DEV_CLASSES_ONLY_IN_DEV_PROFILES.check(classes);
    }

    @Test
    void rule7EntitiesArePortable() {
        ArchitectureRules.ENTITIES_EXTEND_BASE_ENTITY.check(classes);
        ArchitectureRules.ENTITY_FIELDS_ARE_PORTABLE.check(classes);
    }

    @Test
    void rule8ScheduledOnlyInJobs() {
        ArchitectureRules.SCHEDULED_ONLY_IN_JOBS.check(classes);
    }

    @Test
    void modulesNeverUseForeignRepositories() {
        ArchitectureRules.NO_FOREIGN_REPOSITORIES.check(classes);
    }
}
