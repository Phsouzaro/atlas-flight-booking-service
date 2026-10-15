package com.atlas.shared.ratelimit;

import java.util.List;

import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.atlas.shared.ratelimit.RateLimitProperties.Key;
import com.atlas.shared.ratelimit.RateLimitProperties.Rule;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Aplica as regras de {@code app.rate-limit.rules} antes do controller. Roda depois do Spring Security,
 * então regra por usuário já enxerga o token; sem usuário (rota pública), conta pelo IP.
 * <p>
 * O IP é o de {@link HttpServletRequest#getRemoteAddr()}. Atrás do nginx do front, o Tomcat troca pelo
 * do X-Forwarded-For porque {@code server.forward-headers-strategy=native} só confia em proxy de rede interna.
 */
class RateLimitInterceptor implements HandlerInterceptor {

    private record CompiledRule(String name, String method, PathPattern pattern, Rule rule) {
    }

    private final RateLimiter limiter;
    private final List<CompiledRule> rules;

    RateLimitInterceptor(RateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.rules = props.rules().entrySet().stream()
                .map(e -> new CompiledRule(e.getKey(), e.getValue().method(),
                        PathPatternParser.defaultInstance.parse(e.getValue().path()), e.getValue()))
                .toList();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        for (CompiledRule r : rules) {
            if (r.method().equalsIgnoreCase(request.getMethod()) && r.pattern().matches(path)) {
                String key = r.name() + ":" + who(request, r.rule().key());
                limiter.tryConsume(key, r.rule().limit()).ifPresent(wait -> {
                    throw new TooManyRequestsException(wait);
                });
            }
        }
        return true;
    }

    private static String who(HttpServletRequest request, Key key) {
        if (key == Key.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                return "user:" + auth.getName();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
