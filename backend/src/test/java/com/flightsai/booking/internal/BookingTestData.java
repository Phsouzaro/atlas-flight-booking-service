package com.flightsai.booking.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Catálogo mínimo (GRU → GIG, só ECONOMY_LIGHT a 399,90) e atalhos para os testes de reserva. */
final class BookingTestData {

    static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    static final Instant DEPARTURE = Instant.parse("2026-03-15T11:00:00Z");
    static final String FARE = "ECONOMY_LIGHT";
    static final BigDecimal PRICE = new BigDecimal("399.90");

    private BookingTestData() {
    }

    static void reset(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE bookings, idempotency_keys, audit_log, flights, airports CASCADE");
        jdbc.update("INSERT INTO airports (code, name, city, country_code, time_zone) VALUES "
                + "('GRU', 'Guarulhos', 'São Paulo', 'BR', 'America/Sao_Paulo'), "
                + "('GIG', 'Galeão', 'Rio de Janeiro', 'BR', 'America/Sao_Paulo')");
    }

    static UUID flight(JdbcTemplate jdbc, String number, Instant departure, int availableSeats) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO flights (id, flight_number, origin_code, destination_code, departure_at, arrival_at) "
                        + "VALUES (?, ?, 'GRU', 'GIG', ?, ?)",
                id, number, departure.atOffset(ZoneOffset.UTC), departure.plusSeconds(3900).atOffset(ZoneOffset.UTC));
        jdbc.update("INSERT INTO seat_inventory (id, flight_id, fare_class_code, total_seats, available_seats, "
                        + "price_amount, currency) VALUES (?, ?, ?, ?, ?, ?, 'BRL')",
                UUID.randomUUID(), id, FARE, Math.max(availableSeats, 50), availableSeats, PRICE);
        return id;
    }

    static int available(JdbcTemplate jdbc, UUID flightId) {
        return jdbc.queryForObject("SELECT available_seats FROM seat_inventory WHERE flight_id = ? AND fare_class_code = ?",
                Integer.class, flightId, FARE);
    }

    static RequestPostProcessor user(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    static Map<String, Object> holdBody(UUID flightId, int passengers) {
        List<Map<String, Object>> pax = new ArrayList<>();
        for (int i = 0; i < passengers; i++) {
            pax.add(Map.of("fullName", "Passageira " + i, "documentNumber", "DOC" + i, "birthDate", "1990-05-20"));
        }
        return Map.of("flightId", flightId, "fareClass", FARE, "passengers", pax);
    }
}
