package network.foodsharing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The ArchUnit rules of docs/conventions.md A4 (+ the A1 cross-module repository rule). */
final class ArchitectureRules {

    static final String ROOT = "network.foodsharing";
    static final String API_PACKAGE = "network.foodsharing.api";
    static final Set<String> SHARED_API_ENUMS = Set.of("ApiErrorCode", "Language", "Role", "Theme");
    static final Set<String> SHARED_PACKAGES = Set.of("config", "security", "common", "api");

    private ArchitectureRules() {
    }

    // 1. Controllers implement exactly one generated *Api interface and declare no mappings themselves.
    static final ArchRule CONTROLLERS_IMPLEMENT_ONE_GENERATED_API = classes()
            .that().areAnnotatedWith(RestController.class)
            .should(implementExactlyOneGeneratedApi())
            .andShould().notBeAnnotatedWith(RequestMapping.class)
            .andShould(declareNoMappingAnnotationsOnMethods());

    // 2. Controllers never touch repositories or JPA.
    static final ArchRule WEB_DOES_NOT_ACCESS_REPOSITORIES = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    static final ArchRule WEB_DOES_NOT_USE_JPA = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    // 3. Generated models stay at the edge (except the shared string enums).
    static final ArchRule GENERATED_MODELS_ONLY_AT_THE_EDGE = noClasses()
            .that().resideOutsideOfPackages("..web..", "..mapper..", "network.foodsharing.common.error..",
                    "network.foodsharing.config..", "network.foodsharing.api..")
            .should().dependOnClassesThat(generatedModelExceptSharedEnums())
            .because("generated models never reach services or entities (plan §5.1.5); ApiErrorCode, Language, "
                    + "Role and Theme are the shared string enums");

    // 4. Mutating services record history.
    static final ArchRule MUTATING_SERVICES_RECORD_HISTORY = methods()
            .that(areTransactionalWritesInServices())
            .should(recordHistory());

    // 5. Only ClockConfig reads the system clock.
    static final ArchRule NO_SYSTEM_CLOCK = noClasses()
            .that().doNotHaveFullyQualifiedName("network.foodsharing.config.ClockConfig")
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(Clock.class, "systemUTC")
            .orShould().callMethod(Clock.class, "systemDefaultZone")
            .orShould().callConstructor(Date.class)
            .because("everything injects java.time.Clock (fixed in e2e and tests)");

    // 6. Dev classes exist only in the local/e2e profiles.
    static final ArchRule DEV_CLASSES_ONLY_IN_DEV_PROFILES = classes()
            .that().resideInAPackage("network.foodsharing.dev..")
            .and().areTopLevelClasses()
            .and().areNotInterfaces()
            .and().doNotHaveSimpleName("package-info")
            .should(haveOnlyDevProfiles())
            .allowEmptyShould(true);

    // 7. Entities extend BaseEntity, no LOB/TEXT, enums as strings.
    static final ArchRule ENTITIES_EXTEND_BASE_ENTITY = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().beAssignableTo(BaseEntity.class);

    static final ArchRule ENTITY_FIELDS_ARE_PORTABLE = fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .or().areDeclaredInClassesThat().haveFullyQualifiedName(BaseEntity.class.getName())
            .should(bePortableColumns());

    // 8. @Scheduled only in *Job classes.
    static final ArchRule SCHEDULED_ONLY_IN_JOBS = methods()
            .that().areAnnotatedWith(Scheduled.class)
            .should().beDeclaredInClassesThat().haveSimpleNameEndingWith("Job");

    // 9. (A25) Every scheduled method is RunnableJob.run() and holds a ShedLock lock (name == jobName(), checked in
    // ArchitectureTest.rule9SchedulerLockNamesMatchJobNames).
    static final ArchRule SCHEDULED_JOBS_ARE_LOCKED = methods()
            .that().areAnnotatedWith(Scheduled.class)
            .should().beAnnotatedWith(SchedulerLock.class)
            .andShould().haveName("run")
            .andShould().beDeclaredInClassesThat().implement(RunnableJob.class);

    // A1. A module never uses another module's repositories.
    static final ArchRule NO_FOREIGN_REPOSITORIES = classes()
            .that().resideInAPackage("network.foodsharing..")
            .should(notDependOnRepositoriesOfOtherModules());

    static List<ArchRule> all() {
        return List.of(CONTROLLERS_IMPLEMENT_ONE_GENERATED_API, WEB_DOES_NOT_ACCESS_REPOSITORIES, WEB_DOES_NOT_USE_JPA,
                GENERATED_MODELS_ONLY_AT_THE_EDGE, MUTATING_SERVICES_RECORD_HISTORY, NO_SYSTEM_CLOCK,
                DEV_CLASSES_ONLY_IN_DEV_PROFILES, ENTITIES_EXTEND_BASE_ENTITY, ENTITY_FIELDS_ARE_PORTABLE,
                SCHEDULED_ONLY_IN_JOBS, SCHEDULED_JOBS_ARE_LOCKED, NO_FOREIGN_REPOSITORIES);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static ArchCondition<JavaClass> implementExactlyOneGeneratedApi() {
        return new ArchCondition<>("implement exactly one interface from " + API_PACKAGE) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                long count = javaClass.getRawInterfaces().stream()
                        .filter(type -> type.getPackageName().equals(API_PACKAGE))
                        .count();
                if (count != 1) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " implements " + count
                            + " generated API interfaces"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> declareNoMappingAnnotationsOnMethods() {
        List<Class<? extends java.lang.annotation.Annotation>> mappings = List.of(RequestMapping.class,
                GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);
        return new ArchCondition<>("declare no request mapping annotations on methods") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getMethods()) {
                    for (JavaAnnotation<JavaMethod> annotation : method.getAnnotations()) {
                        boolean mapping = mappings.stream().anyMatch(type ->
                                annotation.getRawType().isEquivalentTo(type) || annotation.getRawType().isMetaAnnotatedWith(type));
                        if (mapping) {
                            events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                    + " declares @" + annotation.getRawType().getSimpleName()));
                        }
                    }
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> generatedModelExceptSharedEnums() {
        return DescribedPredicate.describe("generated models (except shared enums)", javaClass ->
                javaClass.getPackageName().equals(API_PACKAGE + ".model")
                        && !SHARED_API_ENUMS.contains(javaClass.getSimpleName()));
    }

    static DescribedPredicate<JavaMethod> areTransactionalWritesInServices() {
        return DescribedPredicate.describe("are @Transactional (not read-only) methods in ..service.. "
                + "(outside the history and dev modules)", method -> {
            JavaClass owner = method.getOwner();
            String packageName = owner.getPackageName();
            if (!(packageName.contains(".service") || packageName.startsWith("service") || packageName.endsWith("service"))
                    || packageName.startsWith("network.foodsharing.history")
                    || packageName.startsWith("network.foodsharing.dev")) {
                return false;
            }
            if (method.getModifiers().contains(JavaModifier.SYNTHETIC) || method.getModifiers().contains(JavaModifier.BRIDGE)
                    || method.getModifiers().contains(JavaModifier.STATIC)) {
                return false;
            }
            Optional<Transactional> own = method.tryGetAnnotationOfType(Transactional.class);
            if (own.isPresent()) {
                return !own.get().readOnly();
            }
            Optional<Transactional> onClass = owner.tryGetAnnotationOfType(Transactional.class);
            return onClass.isPresent() && !onClass.get().readOnly() && method.getModifiers().contains(JavaModifier.PUBLIC);
        });
    }

    static ArchCondition<JavaMethod> recordHistory() {
        return new ArchCondition<>("record history (call HistoryService, call a service method that does, "
                + "or be annotated @NoHistory(reason))") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.isAnnotatedWith(NoHistory.class)) {
                    return;
                }
                if (callsHistoryServiceDirectly(method)) {
                    return;
                }
                for (JavaMethodCall call : callsOf(method)) {
                    JavaClass target = call.getTargetOwner();
                    if (!target.getPackageName().contains("service")) {
                        continue;
                    }
                    Optional<JavaMethod> resolved = call.getTarget().resolveMember();
                    if (resolved.isPresent() && callsHistoryServiceDirectly(resolved.get())) {
                        return;
                    }
                }
                events.add(SimpleConditionEvent.violated(method, method.getFullName()
                        + " is a mutating @Transactional service method but records no history"));
            }
        };
    }

    private static boolean callsHistoryServiceDirectly(JavaMethod method) {
        return callsOf(method).stream().anyMatch(call -> call.getTargetOwner().isAssignableTo(HistoryService.class));
    }

    /** Calls of the method, including those inside its lambdas (compiled to synthetic lambda$name$N methods). */
    private static List<JavaMethodCall> callsOf(JavaMethod method) {
        List<JavaMethodCall> calls = new ArrayList<>(method.getMethodCallsFromSelf());
        String lambdaPrefix = "lambda$" + method.getName() + "$";
        for (JavaMethod candidate : method.getOwner().getMethods()) {
            if (candidate.getName().startsWith(lambdaPrefix)) {
                calls.addAll(candidate.getMethodCallsFromSelf());
            }
        }
        return calls;
    }

    private static ArchCondition<JavaClass> haveOnlyDevProfiles() {
        return new ArchCondition<>("be annotated @Profile with only local/e2e") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Optional<Profile> profile = javaClass.tryGetAnnotationOfType(Profile.class);
                boolean ok = profile.isPresent() && profile.get().value().length > 0
                        && Arrays.stream(profile.get().value()).allMatch(value -> value.equals("local") || value.equals("e2e"));
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName()
                            + " is not annotated @Profile({\"local\",\"e2e\"})"));
                }
            }
        };
    }

    private static ArchCondition<JavaField> bePortableColumns() {
        return new ArchCondition<>("be portable columns (no @Lob, no TEXT/CLOB, enums @Enumerated(STRING))") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                if (field.getModifiers().contains(JavaModifier.STATIC)) {
                    return;
                }
                if (field.isAnnotatedWith(Lob.class)) {
                    events.add(SimpleConditionEvent.violated(field, field.getFullName() + " uses @Lob"));
                }
                field.tryGetAnnotationOfType(Column.class).ifPresent(column -> {
                    String definition = column.columnDefinition().toUpperCase(Locale.ROOT);
                    if (definition.contains("TEXT") || definition.contains("CLOB")) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName() + " uses " + definition));
                    }
                });
                if (field.getRawType().isEnum()) {
                    Optional<Enumerated> enumerated = field.tryGetAnnotationOfType(Enumerated.class);
                    if (enumerated.isEmpty() || enumerated.get().value() != EnumType.STRING) {
                        events.add(SimpleConditionEvent.violated(field, field.getFullName()
                                + " is an enum without @Enumerated(EnumType.STRING)"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notDependOnRepositoriesOfOtherModules() {
        return new ArchCondition<>("not depend on repositories of other modules") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String origin = moduleOf(javaClass.getPackageName());
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetPackage = target.getPackageName();
                    if (targetPackage.startsWith(ROOT + ".") && targetPackage.contains(".repository")) {
                        String targetModule = moduleOf(targetPackage);
                        if (!targetModule.equals(origin)) {
                            events.add(SimpleConditionEvent.violated(javaClass, dependency.getDescription()));
                        }
                    }
                }
            }
        };
    }

    static String moduleOf(String packageName) {
        if (!packageName.startsWith(ROOT + ".")) {
            return "";
        }
        String rest = packageName.substring(ROOT.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
