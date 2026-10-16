package com.atlas.booking.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Expira holds vencidos de tempos em tempos. Desligável por {@code app.booking.expiration-job-enabled}. */
@Configuration
@ConditionalOnProperty(prefix = "app.booking", name = "expiration-job-enabled", havingValue = "true",
        matchIfMissing = true)
class BookingExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationJob.class);

    private final BookingService bookings;

    BookingExpirationJob(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "${app.booking.expiration-interval:PT30S}")
    void expireDueHolds() {
        int expired = bookings.expireDueHolds();
        if (expired > 0) {
            log.info("Expired {} booking holds", expired);
        }
    }
}
