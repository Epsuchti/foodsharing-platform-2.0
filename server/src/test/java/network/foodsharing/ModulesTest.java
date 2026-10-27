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
}
