package com.atlas.catalog.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Catálogo simulado do perfil local. Tudo é função da data: mesma data, mesmos voos, ids,
 * assentos e preços. Não há {@code Random}.
 */
final class CatalogSeedData {

    static final int DAYS = 60;
    static final String CURRENCY = "BRL";

    /** Fuso usado para decidir qual é o "hoje" do seeder. */
    static final ZoneId SCHEDULE_ZONE = ZoneId.of("America/Sao_Paulo");

    record SeedAirport(String code, String name, String city, String countryCode, String timeZone) {
    }

    record Route(String origin, String destination, int durationMinutes, int basePrice,
                 boolean international, List<LocalTime> departures) {
    }

    record SeedFare(UUID id, String fareClass, int totalSeats, int availableSeats, BigDecimal price) {
    }

    record SeedFlight(UUID id, String flightNumber, String origin, String destination,
                      Instant departureAt, Instant arrivalAt, List<SeedFare> fares) {
    }

    /** Assentos e multiplicador de preço de cada classe, na ordem da migration V3. */
    private record FareRule(String fareClass, int domesticSeats, int internationalSeats, BigDecimal multiplier) {
    }

    static final List<SeedAirport> AIRPORTS = List.of(
            new SeedAirport("GRU", "Aeroporto Internacional de São Paulo/Guarulhos", "São Paulo", "BR", "America/Sao_Paulo"),
            new SeedAirport("CGH", "Aeroporto de Congonhas", "São Paulo", "BR", "America/Sao_Paulo"),
            new SeedAirport("GIG", "Aeroporto Internacional do Rio de Janeiro/Galeão", "Rio de Janeiro", "BR", "America/Sao_Paulo"),
            new SeedAirport("SDU", "Aeroporto Santos Dumont", "Rio de Janeiro", "BR", "America/Sao_Paulo"),
            new SeedAirport("BSB", "Aeroporto Internacional de Brasília", "Brasília", "BR", "America/Sao_Paulo"),
            new SeedAirport("CNF", "Aeroporto Internacional de Belo Horizonte/Confins", "Belo Horizonte", "BR", "America/Sao_Paulo"),
            new SeedAirport("SSA", "Aeroporto Internacional de Salvador", "Salvador", "BR", "America/Bahia"),
            new SeedAirport("REC", "Aeroporto Internacional do Recife", "Recife", "BR", "America/Recife"),
            new SeedAirport("POA", "Aeroporto Internacional Salgado Filho", "Porto Alegre", "BR", "America/Sao_Paulo"),
            new SeedAirport("MIA", "Miami International Airport", "Miami", "US", "America/New_York"),
            new SeedAirport("JFK", "John F. Kennedy International Airport", "Nova York", "US", "America/New_York"),
            new SeedAirport("LIS", "Aeroporto Humberto Delgado", "Lisboa", "PT", "Europe/Lisbon"));

    static final List<Route> ROUTES = routes();

    private static final List<FareRule> FARE_RULES = List.of(
            new FareRule("ECONOMY_LIGHT", 120, 200, new BigDecimal("1.00")),
            new FareRule("ECONOMY_FLEX", 36, 60, new BigDecimal("1.40")),
            new FareRule("BUSINESS", 12, 28, new BigDecimal("3.20")));

    private static final Map<String, ZoneId> ZONES = AIRPORTS.stream()
            .collect(Collectors.toUnmodifiableMap(SeedAirport::code, a -> ZoneId.of(a.timeZone())));

    private CatalogSeedData() {
    }

    /** Voos de {@code start} até {@code start + days - 1}, horários no fuso de cada origem. */
    static List<SeedFlight> flights(LocalDate start, int days) {
        List<SeedFlight> result = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);
            for (int r = 0; r < ROUTES.size(); r++) {
                Route route = ROUTES.get(r);
                for (int t = 0; t < route.departures().size(); t++) {
                    result.add(flight(date, r, t, route));
                }
            }
        }
        return result;
    }

    private static SeedFlight flight(LocalDate date, int routeIndex, int departureIndex, Route route) {
        String number = "AT" + (100 + routeIndex * 10 + departureIndex);
        UUID id = uuid("flight:" + number + ":" + date);
        Instant departure = ZonedDateTime.of(date, route.departures().get(departureIndex), ZONES.get(route.origin()))
                .toInstant();
        List<SeedFare> fares = new ArrayList<>();
        for (int f = 0; f < FARE_RULES.size(); f++) {
            FareRule rule = FARE_RULES.get(f);
            int total = route.international() ? rule.internationalSeats() : rule.domesticSeats();
            // ocupação pseudoaleatória, mas fixa para a data: dá voo cheio, quase cheio e vazio
            long hash = mix(date.toEpochDay(), routeIndex * 10L + departureIndex, f);
            int available = total - (int) Math.floorMod(hash, total + 1L);
            BigDecimal price = BigDecimal.valueOf(route.basePrice())
                    .multiply(rule.multiplier())
                    .setScale(0, RoundingMode.HALF_UP)
                    .add(new BigDecimal("0.90"));
            fares.add(new SeedFare(uuid("fare:" + id + ":" + rule.fareClass()), rule.fareClass(), total, available, price));
        }
        return new SeedFlight(id, number, route.origin(), route.destination(),
                departure, departure.plusSeconds(route.durationMinutes() * 60L), List.copyOf(fares));
    }

    private static List<Route> routes() {
        List<Route> routes = new ArrayList<>();
        pair(routes, "CGH", "SDU", 60, 349, false, times("07:00", "12:00", "18:30"), times("08:30", "13:30", "20:00"));
        pair(routes, "GRU", "GIG", 65, 399, false, times("07:30", "19:00"), times("09:00", "20:30"));
        pair(routes, "GRU", "BSB", 100, 529, false, times("08:00", "17:00"), times("10:30", "19:30"));
        pair(routes, "GRU", "SSA", 145, 689, false, times("09:15"), times("13:00"));
        pair(routes, "GRU", "REC", 190, 799, false, times("10:00"), times("14:30"));
        pair(routes, "GRU", "POA", 95, 489, false, times("06:45", "16:30"), times("09:00", "19:00"));
        pair(routes, "CGH", "CNF", 70, 429, false, times("07:15", "18:00"), times("09:00", "19:45"));
        pair(routes, "GIG", "BSB", 105, 549, false, times("08:30"), times("11:30"));
        pair(routes, "GIG", "SSA", 125, 619, false, times("11:00"), times("14:15"));
        pair(routes, "BSB", "REC", 150, 659, false, times("14:00"), times("17:45"));
        pair(routes, "GRU", "MIA", 515, 3290, true, times("23:10"), times("20:30"));
        pair(routes, "GRU", "JFK", 590, 3890, true, times("22:00"), times("21:15"));
        pair(routes, "GRU", "LIS", 610, 3590, true, times("21:30"), times("11:45"));
        pair(routes, "GIG", "LIS", 590, 3490, true, times("20:45"), times("12:30"));
        return List.copyOf(routes);
    }

    private static void pair(List<Route> routes, String a, String b, int minutes, int price, boolean international,
                             List<LocalTime> outbound, List<LocalTime> inbound) {
        routes.add(new Route(a, b, minutes, price, international, outbound));
        routes.add(new Route(b, a, minutes, price, international, inbound));
    }

    private static List<LocalTime> times(String... values) {
        return Arrays.stream(values).map(LocalTime::parse).toList();
    }

    private static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(UTF_8));
    }

    private static long mix(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }
}
