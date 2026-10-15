package com.atlas.catalog;

/** Aeroporto. {@code timeZone} é o id IANA, usado para interpretar a data da busca. */
public record AirportView(String code, String name, String city, String countryCode, String timeZone) {
}
