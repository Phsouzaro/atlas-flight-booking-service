package com.atlas.identity.internal;

import java.time.Clock;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.atlas.shared.ratelimit.RateLimitProperties;
import com.atlas.shared.ratelimit.RateLimiter;
import com.atlas.shared.ratelimit.TooManyRequestsException;

@Service
class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final Clock clock;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;

    /** Hash descartável: comparar mesmo quando o usuário não existe evita revelar emails cadastrados pelo tempo de resposta. */
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, SessionService sessions, Clock clock,
                RateLimiter rateLimiter, RateLimitProperties rateLimits) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(email, passwordEncoder.encode(request.password()), UserRole.USER, clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException race) {
            // duas requisições simultâneas com o mesmo email: o índice único decide
            throw new EmailAlreadyRegisteredException();
        }
        return UserResponse.from(user);
    }

    /**
     * Além do limite por IP (na rota), conta senhas erradas por email: força bruta distribuída em muitos IPs
     * contra uma conta para no limite. Login certo não gasta ficha. O bloqueio vem antes de comparar a senha,
     * então nem a senha certa entra enquanto durar, e a resposta é a mesma exista o email ou não.
     */
    @Transactional // grava o refresh token da sessão nova
    SessionService.Session login(LoginRequest request) {
        String email = normalize(request.email());
        String failuresKey = "login-failures:" + email;
        if (rateLimits.enabled()) {
            rateLimiter.check(failuresKey, rateLimits.loginFailures()).ifPresent(wait -> {
                throw new TooManyRequestsException(wait);
            });
        }
        User user = users.findByEmail(email).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            if (rateLimits.enabled()) {
                rateLimiter.consume(failuresKey, rateLimits.loginFailures());
            }
            throw new InvalidCredentialsException();
        }
        return sessions.start(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
