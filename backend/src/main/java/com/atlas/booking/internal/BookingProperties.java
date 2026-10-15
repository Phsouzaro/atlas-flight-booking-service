package com.atlas.booking.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração das reservas. O job de expiração tem mais duas chaves, lidas direto pelo
 * {@link BookingExpirationJob}: {@code app.booking.expiration-job-enabled} e
 * {@code app.booking.expiration-interval}.
 *
 * @param holdTtl             quanto tempo um hold segura os assentos (padrão 15 min)
 * @param expirationBatchSize quantos holds vencidos o job processa por rodada (padrão 100)
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(Duration holdTtl, int expirationBatchSize) {

    public BookingProperties {
        if (holdTtl == null) {
            holdTtl = Duration.ofMinutes(15);
        }
        if (expirationBatchSize <= 0) {
            expirationBatchSize = 100;
        }
    }
}
