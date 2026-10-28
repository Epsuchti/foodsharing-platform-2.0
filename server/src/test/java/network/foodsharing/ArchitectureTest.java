package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.RunnableJob;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    void rule9ScheduledJobsAreLocked() {
        ArchitectureRules.SCHEDULED_JOBS_ARE_LOCKED.check(classes);
    }

    @Test
    void rule9SchedulerLockNamesMatchJobNames() throws Exception {
        List<String> problems = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.isAssignableTo(RunnableJob.class) || javaClass.isInterface()) {
                continue;
            }
            Class<?> type = javaClass.reflect();
            Method run = type.getMethod("run");
            SchedulerLock lock = run.getAnnotation(SchedulerLock.class);
            // jobName() returns a constant, so a mock calling the real method answers it without dependencies.
            RunnableJob job = (RunnableJob) Mockito.mock(type, Mockito.CALLS_REAL_METHODS);
            if (lock == null || !lock.name().equals(job.jobName())) {
                problems.add(type.getSimpleName() + ": @SchedulerLock " + (lock == null ? "missing" : lock.name())
                        + " != jobName() " + job.jobName());
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void modulesNeverUseForeignRepositories() {
        ArchitectureRules.NO_FOREIGN_REPOSITORIES.check(classes);
    }
}
