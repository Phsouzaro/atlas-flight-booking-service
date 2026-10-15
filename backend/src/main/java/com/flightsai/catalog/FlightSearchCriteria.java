package com.flightsai.catalog;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Critérios da busca de voos (só ida).
 *
 * @param origin      código IATA da origem
 * @param destination código IATA do destino
 * @param date        dia da partida no fuso da origem
 * @param passengers  quantidade de passageiros, de 1 a {@link #MAX_PASSENGERS}
 */
public record FlightSearchCriteria(String origin, String destination, LocalDate date, int passengers) {

    public static final int MAX_PASSENGERS = 9;

    public FlightSearchCriteria {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(date, "date");
    }
}
