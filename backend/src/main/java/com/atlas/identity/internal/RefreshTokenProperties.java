package com.atlas.identity.internal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh token: vai num cookie HttpOnly + SameSite=Strict, então o JavaScript da página não o lê e outro
 * site não consegue mandá-lo.
 *
 * @param ttl         tempo sem uso até a sessão acabar; cada rotação recomeça a contagem
 * @param maxLifetime teto da sessão desde o login, mesmo com uso contínuo
 * @param reuseGrace  janela em que reapresentar um token recém-trocado é corrida entre abas, não roubo
 */
@ConfigurationProperties(prefix = "app.security.refresh")
public record RefreshTokenProperties(Duration ttl, Duration maxLifetime, Duration reuseGrace, String cookieName,
                                     String cookiePath, Boolean cookieSecure) {

    public RefreshTokenProperties {
        ttl = ttl == null ? Duration.ofDays(7) : ttl;
        maxLifetime = maxLifetime == null ? Duration.ofDays(30) : maxLifetime;
        reuseGrace = reuseGrace == null ? Duration.ofSeconds(10) : reuseGrace;
        cookieName = cookieName == null || cookieName.isBlank() ? "atlas_refresh" : cookieName;
        cookiePath = cookiePath == null || cookiePath.isBlank() ? "/" : cookiePath;
        cookieSecure = cookieSecure == null || cookieSecure;
        if (ttl.compareTo(maxLifetime) > 0) {
            throw new IllegalArgumentException("app.security.refresh.ttl cannot be longer than max-lifetime");
        }
    }
}
