package com.atlas.catalog.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.atlas.catalog.AirportView;
import com.atlas.catalog.FlightSearchApi;
import com.atlas.catalog.FlightSearchCriteria;
import com.atlas.catalog.FlightView;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@RestController
class CatalogController {

    private static final String IATA = "[A-Za-z]{3}";

    private final FlightSearchApi catalog;

    CatalogController(FlightSearchApi catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/airports")
    List<AirportView> airports() {
        return catalog.airports();
    }

    /** Só ida. {@code date} é o dia da partida no fuso da origem. */
    @GetMapping("/flights")
    List<FlightView> searchFlights(
            @RequestParam @Pattern(regexp = IATA) String origin,
            @RequestParam @Pattern(regexp = IATA) String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") @Min(1) @Max(FlightSearchCriteria.MAX_PASSENGERS) int pax) {
        return catalog.search(new FlightSearchCriteria(origin, destination, date, pax));
    }

    @GetMapping("/flights/{id}")
    FlightView flight(@PathVariable UUID id) {
        return catalog.findFlight(id).orElseThrow(() -> new FlightNotFoundException(id));
    }
}
