package com.atlas.catalog.internal;

import static java.time.ZoneOffset.UTC;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.atlas.catalog.internal.CatalogSeedData.SeedFlight;

/**
 * Popula o catálogo simulado no perfil local: aeroportos e voos dos próximos
 * {@value CatalogSeedData#DAYS} dias.
 * <p>
 * Roda a cada subida e só insere o que falta ({@code ON CONFLICT DO NOTHING}, ids determinísticos),
 * então completa a janela conforme os dias passam e nunca sobrescreve assento já vendido.
 */
@Component
@Profile("local")
class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    CatalogSeeder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(clock.withZone(CatalogSeedData.SCHEDULE_ZONE));
        List<SeedFlight> flights = CatalogSeedData.flights(today, CatalogSeedData.DAYS);

        int airports = sum(jdbc.batchUpdate(
                "INSERT INTO airports (code, name, city, country_code, time_zone) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT DO NOTHING",
                CatalogSeedData.AIRPORTS.stream()
                        .map(a -> new Object[] {a.code(), a.name(), a.city(), a.countryCode(), a.timeZone()})
                        .toList()));

        int inserted = sum(jdbc.batchUpdate(
                "INSERT INTO flights (id, flight_number, origin_code, destination_code, departure_at, arrival_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                flights.stream()
                        .map(f -> new Object[] {f.id(), f.flightNumber(), f.origin(), f.destination(),
                                utc(f.departureAt()), utc(f.arrivalAt())})
                        .toList()));

        jdbc.batchUpdate(
                "INSERT INTO seat_inventory (id, flight_id, fare_class_code, total_seats, available_seats, "
                        + "price_amount, currency) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                flights.stream()
                        .flatMap(f -> f.fares().stream().map(s -> new Object[] {s.id(), f.id(), s.fareClass(),
                                s.totalSeats(), s.availableSeats(), s.price(), CatalogSeedData.CURRENCY}))
                        .toList());

        log.info("Catalog seed from {}: {} airports and {} flights inserted ({} flights in the window)",
                today, airports, inserted, flights.size());
    }

    /** O driver do Postgres não aceita {@link Instant} direto. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(UTC);
    }

    private static int sum(int[] counts) {
        return Arrays.stream(counts).filter(c -> c > 0).sum();
    }
}
