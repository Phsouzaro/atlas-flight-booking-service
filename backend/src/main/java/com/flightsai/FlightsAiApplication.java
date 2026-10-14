package com.flightsai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FlightsAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightsAiApplication.class, args);
    }
}
