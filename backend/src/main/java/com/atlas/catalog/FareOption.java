package com.atlas.catalog;

import com.atlas.shared.Money;

/** Uma tarifa de um voo: classe, assentos disponíveis e preço por passageiro. */
public record FareOption(String fareClass, String name, String cabin, int availableSeats, Money price) {
}
