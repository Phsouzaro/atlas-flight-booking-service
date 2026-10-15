package com.flightsai.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * Assento e preço de uma tarifa, para o módulo de reservas.
 * <p>
 * Por enquanto só consulta. Reservar e devolver assento entram na fase 2, junto com a decisão de
 * concorrência (ADR-004).
 */
public interface AvailabilityApi {

    Optional<FareAvailability> findFare(UUID flightId, String fareClass);
}
