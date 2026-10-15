/**
 * Módulo de catálogo: aeroportos, voos, classes tarifárias e inventário de assentos.
 * <p>
 * API pública (este pacote): {@link com.atlas.catalog.FlightSearchApi} para busca (REST e,
 * na fase 4, as tools da IA) e {@link com.atlas.catalog.AvailabilityApi} para o módulo de
 * reservas consultar assento e preço. Tudo em {@code catalog.internal} é privado.
 */
@ApplicationModule(displayName = "Catalog")
package com.atlas.catalog;

import org.springframework.modulith.ApplicationModule;
