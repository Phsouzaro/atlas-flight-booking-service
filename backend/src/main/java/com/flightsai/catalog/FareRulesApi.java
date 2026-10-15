package com.flightsai.catalog;

import java.util.Optional;

/** Regras tarifárias, usadas pelo módulo de reservas para cotar cancelamento e remarcação. */
public interface FareRulesApi {

    Optional<FareRules> rulesFor(String fareClass);
}
