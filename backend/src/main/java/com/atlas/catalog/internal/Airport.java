package com.atlas.catalog.internal;

import java.time.ZoneId;

import org.hibernate.annotations.Immutable;

import com.atlas.catalog.AirportView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "airports")
class Airport {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    protected Airport() {
    }

    String getCode() {
        return code;
    }

    ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    AirportView toView() {
        return new AirportView(code, name, city, countryCode, timeZone);
    }
}
