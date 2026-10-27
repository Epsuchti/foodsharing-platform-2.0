package network.foodsharing;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Spring Modulith: module boundaries (only service/domain/mapper named interfaces) and no cycles. */
class ModulesTest {

    private final ApplicationModules modules = ApplicationModules.of(FoodsharingApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }
}
