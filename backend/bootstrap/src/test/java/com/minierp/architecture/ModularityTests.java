package com.minierp.architecture;

import com.minierp.MiniErpApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(MiniErpApplication.class);

    @Test
    void verifies_module_boundaries() {
        modules.verify();
    }

    @Test
    void writes_documentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
