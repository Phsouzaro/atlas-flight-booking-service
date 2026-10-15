package com.atlas.catalog;

import java.util.Optional;
import java.util.UUID;

/**
 * Assento e preço de uma tarifa, para o módulo de reservas.
 * <p>
 * Reservar e devolver são updates condicionais no banco (ADR-004): atômicos, sem lock longo e sem
 * retry. Os dois participam da transação de quem chama.
 */
public interface AvailabilityApi {

    Optional<FareAvailability> findFare(UUID flightId, String fareClass);

    /**
     * Tira {@code seats} assentos da tarifa, se houver.
     *
     * @return {@code false} se não houver {@code seats} assentos disponíveis (ou a tarifa não existir);
     *         nesse caso nada muda
     */
    boolean reserveSeats(UUID flightId, String fareClass, int seats);

    /**
     * Devolve {@code seats} assentos à tarifa (hold expirado, cancelamento).
     *
     * @throws IllegalStateException se a devolução passar do total de assentos, o que indica
     *         devolução em dobro
     */
    void releaseSeats(UUID flightId, String fareClass, int seats);
}
