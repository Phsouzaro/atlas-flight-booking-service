/**
 * Módulo de identidade: cadastro, login e emissão de JWT.
 * <p>
 * API pública (este pacote): {@link com.flightsai.identity.CurrentUser}.
 * Tudo em {@code identity.internal} é privado e verificado pelo Spring Modulith.
 */
@ApplicationModule(displayName = "Identity")
package com.flightsai.identity;

import org.springframework.modulith.ApplicationModule;
