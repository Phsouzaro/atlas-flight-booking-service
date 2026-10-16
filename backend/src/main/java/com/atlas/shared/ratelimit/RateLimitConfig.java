package com.atlas.shared.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Liga o interceptor (se {@code app.rate-limit.enabled}) e responde 429 em problem+json com Retry-After. */
@Configuration
class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimiter limiter;
    private final RateLimitProperties props;

    RateLimitConfig(RateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (props.enabled()) {
            registry.addInterceptor(new RateLimitInterceptor(limiter, props));
        }
    }

    @RestControllerAdvice
    static class TooManyRequestsHandler {

        @ExceptionHandler(TooManyRequestsException.class)
        ResponseEntity<ProblemDetail> onTooMany(TooManyRequestsException ex) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
            problem.setTitle("Too Many Requests");
            problem.setProperty("reason", "RATE_LIMITED");
            problem.setProperty("retryAfterSeconds", ex.retryAfterSeconds());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                    .body(problem);
        }
    }
}
