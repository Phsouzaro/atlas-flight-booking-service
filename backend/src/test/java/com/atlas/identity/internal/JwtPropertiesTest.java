package com.atlas.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("curto", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultsTtlToOneHour() {
        JwtProperties props = new JwtProperties("x".repeat(32), null);
        assertThat(props.ttl()).isEqualTo(Duration.ofHours(1));
    }
}
