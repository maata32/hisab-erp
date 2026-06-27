package com.hisaberp.architecture;

import com.hisaberp.HisabErpApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(HisabErpApplication.class);

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
