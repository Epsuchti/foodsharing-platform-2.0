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
            // M6 readers (A38): calendar, map, activity and search may read events/forum/polls/mailboxes; never messaging.
            java.util.Set<String> readers = "messaging".equals(communication) ? java.util.Set.of()
                    : java.util.Set.of("calendar", "map", "activity", "search");
            modules.stream().filter(module -> !module.equals(target))
                    .filter(module -> !readers.contains(module.getName())).forEach(module ->
                    assertThat(module.getDirectDependencies(modules).contains(target)).as(module.getName() + " → " + communication)
                            .isFalse());
        }
    }

    @Test
    void m6ModulesHaveOnlyTheirAllowedDependencies() {
        java.util.Map<String, java.util.Set<String>> allowed = java.util.Map.ofEntries(
                java.util.Map.entry("calendar", java.util.Set.of("user", "region", "event", "pickup", "intropickup", "history")),
                java.util.Map.entry("basket", java.util.Set.of("user", "region", "store", "notification", "history", "upload", "admin")),
                java.util.Map.entry("fsp", java.util.Set.of("user", "region", "notification", "history", "upload", "admin")),
                java.util.Map.entry("map", java.util.Set.of("region", "store", "fsp", "basket", "event", "user", "pickup", "admin")),
                java.util.Map.entry("activity", java.util.Set.of("forum", "store", "mailbox", "event", "fsp", "user", "region")),
                java.util.Map.entry("report", java.util.Set.of("user", "region", "store", "forum", "notification", "history")),
                java.util.Map.entry("achievement", java.util.Set.of("user", "region", "store", "quiz", "notification", "history")),
                java.util.Map.entry("statistics", java.util.Set.of("user", "region", "store", "pickup", "fsp", "basket", "history")),
                java.util.Map.entry("search", java.util.Set.of("user", "region", "store", "forum", "fsp", "event", "poll", "mailbox", "history", "admin")),
                java.util.Map.entry("resource", java.util.Set.of("user", "region", "notification", "history", "upload", "admin")),
                java.util.Map.entry("support", java.util.Set.of("user", "notification", "upload", "history", "admin")),
                java.util.Map.entry("oauthserver", java.util.Set.of("user", "region", "auth", "history", "admin")),
                java.util.Map.entry("profile", java.util.Set.of("user", "region", "store", "pickup", "notification",
                        "history")));
        java.util.Set<String> shared = java.util.Set.of("config", "security", "common", "api");
        allowed.forEach((name, permitted) -> {
            ApplicationModule module = modules.getModuleByName(name).orElseThrow();
            assertThat(module.getDirectDependencies(modules).uniqueModules().map(ApplicationModule::getName)
                    .filter(dependency -> !shared.contains(dependency)).toList())
                    .as(name).allMatch(permitted::contains);
        });
        for (String leaf : new String[] {"activity", "profile"}) {
            ApplicationModule leafModule = modules.getModuleByName(leaf).orElseThrow();
            modules.stream().forEach(module -> assertThat(module.getDirectDependencies(modules).contains(leafModule))
                    .as(module.getName() + " → " + leaf).isFalse());
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
