package com.atlas.booking;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Pedido de hold: um voo, uma classe tarifária, de 1 a {@value #MAX_PASSENGERS} passageiros. */
public record HoldBookingCommand(
        @NotNull UUID flightId,
        @NotBlank String fareClass,
        @NotEmpty @Size(max = MAX_PASSENGERS) List<@Valid @NotNull PassengerInfo> passengers) {

    public static final int MAX_PASSENGERS = 9;
}
