/**
 * Módulo de catálogo: aeroportos, voos, classes tarifárias e inventário de assentos.
 * <p>
 * API pública (este pacote): {@link com.flightsai.catalog.FlightSearchApi} para busca (REST e,
 * na fase 4, as tools da IA) e {@link com.flightsai.catalog.AvailabilityApi} para o módulo de
 * reservas consultar assento e preço. Tudo em {@code catalog.internal} é privado.
 */
@ApplicationModule(displayName = "Catalog")
package com.flightsai.catalog;

import org.springframework.modulith.ApplicationModule;
