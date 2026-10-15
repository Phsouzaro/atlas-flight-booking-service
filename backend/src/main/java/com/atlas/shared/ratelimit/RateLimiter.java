package com.atlas.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.atlas.shared.ratelimit.RateLimitProperties.Limit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

/**
 * Token bucket por chave (ex.: {@code login:203.0.113.7}), em memória. Vale para uma instância; com
 * várias, os buckets teriam que ir para Postgres ou Redis (ADR-006).
 * <p>
 * O tempo vem do {@link Clock} da aplicação, para os testes conseguirem avançar o relógio.
 */
@Component
public class RateLimiter {

    /** Buckets sem uso por mais tempo que isso somem; o maior período configurado é 1 dia. */
    private static final Duration IDLE = Duration.ofDays(1);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(IDLE)
            .maximumSize(100_000)
            .build();
    private final TimeMeter time;

    RateLimiter(Clock clock) {
        this.time = new ClockTimeMeter(clock);
    }

    /**
     * Gasta uma ficha.
     *
     * @return vazio se passou; senão, quanto falta para a próxima ficha
     */
    public Optional<Duration> tryConsume(String key, Limit limit) {
        ConsumptionProbe probe = bucket(key, limit).tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Optional.empty() : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    /** Como {@link #tryConsume}, mas sem gastar: para bloquear antes de saber se a tentativa vai contar. */
    public Optional<Duration> check(String key, Limit limit) {
        Bucket bucket = bucket(key, limit);
        if (bucket.getAvailableTokens() > 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofNanos(bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill()));
    }

    /** Gasta uma ficha sem perguntar; o bloqueio fica para o próximo {@link #check}. */
    public void consume(String key, Limit limit) {
        bucket(key, limit).tryConsume(1);
    }

    private Bucket bucket(String key, Limit limit) {
        // a chave leva o limite: mudar a configuração cria buckets novos em vez de reaproveitar os velhos
        return buckets.get(key + "|" + limit.capacity() + "/" + limit.period(), k -> Bucket.builder()
                .addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                .withCustomTimePrecision(time)
                .build());
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
