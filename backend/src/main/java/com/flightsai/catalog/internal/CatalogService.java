package com.flightsai.catalog.internal;

import static com.flightsai.catalog.internal.FlightSpecifications.departsBetween;
import static com.flightsai.catalog.internal.FlightSpecifications.hasSeatsFor;
import static com.flightsai.catalog.internal.FlightSpecifications.route;

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

import com.flightsai.catalog.AirportView;
import com.flightsai.catalog.AvailabilityApi;
import com.flightsai.catalog.FareAvailability;
import com.flightsai.catalog.FareOption;
import com.flightsai.catalog.FlightSearchApi;
import com.flightsai.catalog.FlightSearchCriteria;
import com.flightsai.catalog.FlightView;
import com.flightsai.catalog.InvalidFlightSearchException;

@Service
@Transactional(readOnly = true)
class CatalogService implements FlightSearchApi, AvailabilityApi {

    private static final Sort BY_DEPARTURE = Sort.by("departureAt");

    private final AirportRepository airports;
    private final FlightRepository flights;
    private final SeatInventoryRepository seats;
    private final Clock clock;

    CatalogService(AirportRepository airports, FlightRepository flights, SeatInventoryRepository seats, Clock clock) {
        this.airports = airports;
        this.flights = flights;
        this.seats = seats;
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
                        s.getFareClass().getCode(),
                        s.getAvailableSeats(),
                        s.price(),
                        s.getFlight().getDepartureAt()));
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
