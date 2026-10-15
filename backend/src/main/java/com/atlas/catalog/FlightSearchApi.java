package com.atlas.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consulta ao catálogo. Só leitura. */
public interface FlightSearchApi {

    /** Todos os aeroportos, ordenados pelo código IATA. */
    List<AirportView> airports();

    /**
     * Voos que partem no dia informado (no fuso da origem) e ainda não decolaram, com assento para
     * todos os passageiros, ordenados pela partida. Cada voo traz só as tarifas que comportam os
     * passageiros.
     *
     * @throws InvalidFlightSearchException se os critérios forem inválidos (aeroporto desconhecido,
     *         origem igual ao destino, data no passado, passageiros fora do limite)
     */
    List<FlightView> search(FlightSearchCriteria criteria);

    /** Um voo com todas as tarifas, inclusive as esgotadas. */
    Optional<FlightView> findFlight(UUID flightId);
}
