package com.atlas.shared.observability;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Correlation id = trace id do Micrometer Tracing. Este arquivo o expõe em dois lugares:
 * <ul>
 *   <li>header {@value #TRACE_HEADER} em toda resposta, inclusive 401 do Spring Security (o filtro roda antes
 *   dele), para o usuário ou a UI citarem num chamado;</li>
 *   <li>{@code userId} no MDC depois da autenticação, para cada linha de log dizer de quem era o request.</li>
 * </ul>
 * O trace id já vai para o MDC (e para os logs) pelo próprio Micrometer.
 */
@Configuration
public class TraceHeaders {

    public static final String TRACE_HEADER = "X-Trace-Id";
    static final String USER_MDC = "userId";

    /** Trace id do request atual, se houver tracing ligado. */
    public static Optional<String> currentTraceId(ObjectProvider<Tracer> tracer) {
        Tracer t = tracer.getIfAvailable();
        Span span = t == null ? null : t.currentSpan();
        return span == null ? Optional.empty() : Optional.of(span.context().traceId());
    }

    /** Logo depois do filtro de observação, que abre o span, e antes da segurança. */
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> traceIdHeaderFilter(ObjectProvider<Tracer> tracer) {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                currentTraceId(tracer).ifPresent(id -> response.setHeader(TRACE_HEADER, id));
                chain.doFilter(request, response);
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /** Depois da segurança: aí já se sabe quem é o usuário. */
    @Bean
    FilterRegistrationBean<OncePerRequestFilter> userMdcFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                boolean user = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
                if (user) {
                    MDC.put(USER_MDC, auth.getName());
                }
                try {
                    chain.doFilter(request, response);
                } finally {
                    MDC.remove(USER_MDC);
                }
            }
        });
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
