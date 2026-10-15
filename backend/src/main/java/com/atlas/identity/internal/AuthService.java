package com.atlas.identity.internal;

import java.time.Clock;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;

    /** Hash descartável: comparar mesmo quando o usuário não existe evita revelar emails cadastrados pelo tempo de resposta. */
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
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

    @Transactional(readOnly = true)
    TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
