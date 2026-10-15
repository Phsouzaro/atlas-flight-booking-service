package com.flightsai.catalog;

import com.flightsai.shared.Money;

/** Uma tarifa de um voo: classe, assentos disponíveis e preço por passageiro. */
public record FareOption(String fareClass, String name, String cabin, int availableSeats, Money price) {
}
