/**
 * Código transversal (relógio, configurações comuns). Módulo aberto: qualquer outro módulo
 * pode depender dele. Mantenha pequeno: regra de negócio nunca entra aqui.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared")
package com.flightsai.shared;

import org.springframework.modulith.ApplicationModule;
