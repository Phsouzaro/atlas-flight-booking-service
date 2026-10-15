package com.flightsai.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface AirportRepository extends JpaRepository<Airport, String> {
}
