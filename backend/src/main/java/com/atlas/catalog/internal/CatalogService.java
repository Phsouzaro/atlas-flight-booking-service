package com.atlas.catalog.internal;

import static com.atlas.catalog.internal.FlightSpecifications.departsBetween;
import static com.atlas.catalog.internal.FlightSpecifications.hasSeatsFor;
import static com.atlas.catalog.internal.FlightSpecifications.route;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.atlas.catalog.AirportView;
import com.atlas.catalog.AvailabilityApi;
import com.atlas.catalog.FareAvailability;
import com.atlas.catalog.FareOption;
import com.atlas.catalog.FareRules;
import com.atlas.catalog.FareRulesApi;
import com.atlas.catalog.FlightSearchApi;
import com.atlas.catalog.FlightSearchCriteria;
import com.atlas.catalog.FlightView;
import com.atlas.catalog.InvalidFlightSearchException;

@Service
@Transactional(readOnly = true)
class CatalogService implements FlightSearchApi, AvailabilityApi, FareRulesApi {

    private static final Sort BY_DEPARTURE = Sort.by("departureAt");

    private final AirportRepository airports;
    private final FlightRepository flights;
    private final SeatInventoryRepository seats;
    private final FareClassRepository fareClasses;
    private final Clock clock;

    CatalogService(AirportRepository airports, FlightRepository flights, SeatInventoryRepository seats,
                   FareClassRepository fareClasses, Clock clock) {
        this.airports = airports;
        this.flights = flights;
        this.seats = seats;
        this.fareClasses = fareClasses;
        this.clock = clock;
    }

    @Override
    public List<AirportView> airports() {
        return airports.findAll(Sort.by("code")).stream().map(Airport::toView).toList();
    }

    @Override
    public List<FlightView> search(FlightSearchCriteria criteria) {
        String originCode = normalizeCode(criteria.origin());
        String destinationCode = normalizeCode(criteria.destination());
        if (originCode.equals(destinationCode)) {
            throw new InvalidFlightSearchException("Origin and destination must be different");
        }
        int passengers = criteria.passengers();
        if (passengers < 1 || passengers > FlightSearchCriteria.MAX_PASSENGERS) {
            throw new InvalidFlightSearchException(
                    "Passengers must be between 1 and " + FlightSearchCriteria.MAX_PASSENGERS);
        }
        Airport origin = requireAirport(originCode);
        requireAirport(destinationCode);

        // o "dia" da busca é o dia local da origem, não o dia em UTC
        ZoneId zone = origin.zoneId();
        LocalDate date = criteria.date();
        if (date.isBefore(LocalDate.now(clock.withZone(zone)))) {
            throw new InvalidFlightSearchException("Date must not be in the past");
        }
        Instant now = clock.instant();
        Instant startOfDay = date.atStartOfDay(zone).toInstant();
        Instant from = startOfDay.isAfter(now) ? startOfDay : now; // voo que já decolou não aparece
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        return flights.findAll(
                        route(originCode, destinationCode)
                                .and(departsBetween(from, to))
                                .and(hasSeatsFor(passengers)),
                        BY_DEPARTURE)
                .stream()
                .map(flight -> toView(flight, passengers))
                .toList();
    }

    @Override
    public Optional<FlightView> findFlight(UUID flightId) {
        return flights.findWithSeatsById(flightId).map(flight -> toView(flight, 0));
    }

    @Override
    public Optional<FareAvailability> findFare(UUID flightId, String fareClass) {
        return seats.findByFlightIdAndFareClassCode(flightId, fareClass)
                .map(s -> new FareAvailability(
                        s.getFlight().getId(),
                        s.getFlight().getFlightNumber(),
                        s.getFlight().getOrigin().getCode(),
                        s.getFlight().getDestination().getCode(),
                        s.getFlight().getDepartureAt(),
                        s.getFareClass().getCode(),
                        s.getAvailableSeats(),
                        s.price()));
    }

    @Override
    public Optional<FareRules> rulesFor(String fareClass) {
        return fareClasses.findById(fareClass).map(FareClass::toRules);
    }

    @Override
    @Transactional
    public boolean reserveSeats(UUID flightId, String fareClass, int seats) {
        requirePositive(seats);
        return this.seats.decrementAvailable(flightId, fareClass, seats) == 1;
    }

    @Override
    @Transactional
    public void releaseSeats(UUID flightId, String fareClass, int seats) {
        requirePositive(seats);
        if (this.seats.incrementAvailable(flightId, fareClass, seats) != 1) {
            throw new IllegalStateException(
                    "Cannot release %d seats on flight %s, fare %s".formatted(seats, flightId, fareClass));
        }
    }

    private static void requirePositive(int seats) {
        if (seats < 1) {
            throw new IllegalArgumentException("Seats must be positive: " + seats);
        }
    }

    private Airport requireAirport(String code) {
        return airports.findById(code)
                .orElseThrow(() -> new InvalidFlightSearchException("Unknown airport: " + code));
    }

    /** Monta a visão do voo com as tarifas que têm pelo menos {@code minSeats} assentos. */
    private static FlightView toView(Flight flight, int minSeats) {
        List<FareOption> fares = flight.getSeats().stream()
                .filter(s -> s.getAvailableSeats() >= minSeats)
                .sorted(Comparator.comparingInt(s -> s.getFareClass().getDisplayOrder()))
                .map(s -> new FareOption(
                        s.getFareClass().getCode(),
                        s.getFareClass().getName(),
                        s.getFareClass().getCabin().name(),
                        s.getAvailableSeats(),
                        s.price()))
                .toList();
        Airport origin = flight.getOrigin();
        Airport destination = flight.getDestination();
        return new FlightView(
                flight.getId(),
                flight.getFlightNumber(),
                origin.toView(),
                destination.toView(),
                flight.getDepartureAt().atZone(origin.zoneId()).toOffsetDateTime(),
                flight.getArrivalAt().atZone(destination.zoneId()).toOffsetDateTime(),
                Duration.between(flight.getDepartureAt(), flight.getArrivalAt()).toMinutes(),
                fares);
    }

    private static String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new InvalidFlightSearchException("Airport code must have 3 letters: " + code);
        }
        return normalized;
    }
}
