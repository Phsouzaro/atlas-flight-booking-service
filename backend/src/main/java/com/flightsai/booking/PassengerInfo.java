package com.flightsai.booking;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dados de um passageiro. É PII: não vai para log nem para o audit_log. */
public record PassengerInfo(
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9.\\-]+") String documentNumber,
        @NotNull @Past LocalDate birthDate) {
}
