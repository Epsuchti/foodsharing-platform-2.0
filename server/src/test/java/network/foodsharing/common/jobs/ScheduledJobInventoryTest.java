package network.foodsharing.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C15 / D6.3: every background job of plan §6.3 exists as a scheduled {@link RunnableJob} bean, so the ORGA job list
 * (which lists {@code JobRunner.jobs()}, i.e. every {@code RunnableJob} bean) shows it and can run it by name. Job names
 * are unique. Plain class scan (ArchUnit importer), no Spring context.
 */
class ScheduledJobInventoryTest {

    /** Plan §6.3 → job names (also the ORGA's job list in the operations journey). */
    private static final List<String> SECTION_6_3 = List.of(
            "mail-outbox", // mail outbox worker
            "mailbox-fetcher", // incoming regional mail
            "open-slot-adverts", // open-slot alerts
            "signout-alerts", // sign-out check (daily summary)
            "pickup-reminder", // pickup reminders
            "onboarding-nudges", // onboarding nudges
            "region-maintenance", // parent memberships, sleep mode wake-up, inactive accounts
            "store-maintenance", // unconfirmed slots, empty pickups, inactive store managers
            "region-statistics", // region statistics
            "statistics-aggregation", // platform statistics
            "audit-retention", // audit retention
            "hygiene-expiry", // quiz/achievement expiry
            "cleanup", // push subscriptions, tokens, bells, uploads
            "history-retention", // history retention
            "automation-evaluate", // automation rules
            "request-automation", // request automation
            "newsletter-send", // newsletters
            "telegram-poll"); // telegram bot

    @Test
    void everySection63JobIsAScheduledBeanWithAUniqueName() throws Exception {
        List<String> names = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (JavaClass javaClass : new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("network.foodsharing")) {
            if (!javaClass.isAssignableTo(RunnableJob.class) || javaClass.isInterface()) {
                continue;
            }
            Class<?> type = javaClass.reflect();
            if (Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            Method run = type.getMethod("run");
            if (!type.isAnnotationPresent(Component.class)) {
                problems.add(type.getSimpleName() + " is no @Component");
            }
            if (!run.isAnnotationPresent(Scheduled.class) || !run.isAnnotationPresent(SchedulerLock.class)) {
                problems.add(type.getSimpleName() + ".run() is not @Scheduled with a @SchedulerLock");
            }
            // jobName() returns a constant, so a mock calling the real method answers it without dependencies
            names.add(((RunnableJob) Mockito.mock(type, Mockito.CALLS_REAL_METHODS)).jobName());
        }
        assertThat(problems).isEmpty();
        assertThat(names).doesNotHaveDuplicates().containsAll(SECTION_6_3);
    }
}
