package com.flightsai;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Guarda as fronteiras do monolito modular. Se um módulo acessar o {@code internal} de outro
 * ou criar dependência cíclica, este teste quebra o build.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(FlightsAiApplication.class);

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // gera diagramas PlantUML em target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}
