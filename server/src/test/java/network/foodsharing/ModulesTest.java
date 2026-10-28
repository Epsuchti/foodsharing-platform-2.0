package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith: module boundaries (only service/domain/mapper named interfaces) and no cycles. {@code config},
 * {@code security}, {@code common} and the generated {@code api} are shared (OPEN, docs/conventions.md A1).
 */
class ModulesTest {

    private final ApplicationModules modules = ApplicationModules.of(FoodsharingApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void featureModulesAreClosedAndSharedPackagesOpen() {
        for (String feature : new String[] {"user", "auth", "history"}) {
            assertThat(modules.getModuleByName(feature)).as(feature).isPresent()
                    .get().extracting(ApplicationModule::isOpen).isEqualTo(false);
        }
        for (String shared : new String[] {"config", "security", "common", "api"}) {
            assertThat(modules.getModuleByName(shared)).as(shared).isPresent()
                    .get().extracting(ApplicationModule::isOpen).isEqualTo(true);
        }
    }

    /** M5 modules only depend on what docs/plan-m5.md B1 allows (shared packages aside); nobody depends on them. */
    @Test
    void communicationModulesHaveOnlyTheirAllowedDependencies() {
        java.util.Map<String, java.util.Set<String>> allowed = java.util.Map.of(
                "messaging", java.util.Set.of("user", "region", "store", "notification", "history", "upload"),
                "mailbox", java.util.Set.of("user", "region", "notification", "history", "upload"),
                "forum", java.util.Set.of("region", "user", "notification", "history"),
                "event", java.util.Set.of("region", "user", "notification", "history"),
                "poll", java.util.Set.of("region", "user", "notification", "history"),
                "survey", java.util.Set.of("user", "store", "region", "notification", "history"),
                "upload", java.util.Set.of("user", "history"));
        java.util.Set<String> shared = java.util.Set.of("config", "security", "common", "api");
        allowed.forEach((name, permitted) -> {
            ApplicationModule module = modules.getModuleByName(name).orElseThrow();
            assertThat(module.getDirectDependencies(modules).uniqueModules().map(ApplicationModule::getName)
                    .filter(dependency -> !shared.contains(dependency)).toList())
                    .as(name).allMatch(permitted::contains);
        });
        for (String communication : new String[] {"messaging", "mailbox", "forum", "event", "poll", "survey"}) {
            ApplicationModule target = modules.getModuleByName(communication).orElseThrow();
            modules.stream().filter(module -> !module.equals(target)).forEach(module ->
                    assertThat(module.getDirectDependencies(modules).contains(target)).as(module.getName() + " → " + communication)
                            .isFalse());
        }
    }

    @Test
    void automationIsNeverADependencyOfPickupOrStore() {
        ApplicationModule automation = modules.getModuleByName("automation").orElseThrow();
        for (String feature : new String[] {"pickup", "store", "region", "user", "notification"}) {
            ApplicationModule module = modules.getModuleByName(feature).orElseThrow();
            assertThat(module.getDirectDependencies(modules).contains(automation)).as(feature).isFalse();
        }
    }
}
