package com.atlas.identity.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.atlas.audit.AuditEntry;
import com.atlas.audit.AuditLog;

/**
 * Sessões de login: um par access token (JWT, curto) + refresh token (opaco, rotacionado a cada uso).
 * <p>
 * O refresh token sai daqui uma vez, em claro, e só o hash fica no banco. Reapresentar um token já trocado
 * fora da janela de tolerância é tratado como roubo: a família inteira (o login todo) é revogada.
 */
@Service
class SessionService {

    static final String ENTITY = "user";
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final TokenService accessTokens;
    private final AuditLog audit;
    private final RefreshTokenProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    SessionService(RefreshTokenRepository refreshTokens, UserRepository users, TokenService accessTokens, AuditLog audit,
                   RefreshTokenProperties props, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.accessTokens = accessTokens;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    /** Par de tokens entregue ao cliente. {@code refreshToken} vai no cookie, nunca no corpo. */
    record Session(TokenResponse access, String refreshToken) {
    }

    /** Abre uma sessão nova para um login que acabou de passar. */
    @Transactional
    Session start(User user) {
        String raw = newToken();
        refreshTokens.save(RefreshToken.start(user.getId(), hash(raw), clock.instant(), props));
        return new Session(accessTokens.issue(user), raw);
    }

    /**
     * Troca o refresh token por um par novo. Sem rollback nas recusas: a revogação da família, quando há
     * reuso, precisa ficar gravada mesmo com o 401.
     *
     * @throws InvalidRefreshTokenException token desconhecido, vencido, revogado ou reusado
     * @throws RefreshRaceException         token trocado há segundos por outra aba; basta tentar de novo
     */
    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, RefreshRaceException.class})
    Session rotate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        RefreshToken current = refreshTokens.lockByHash(hash(raw)).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (current.isRevoked()) {
            if (current.wasJustRotated(now, props.reuseGrace())) {
                throw new RefreshRaceException();
            }
            if (current.wasRotated()) {
                // um token já trocado voltou depois da janela: alguém tem uma cópia. Derruba o login todo.
                refreshTokens.revokeFamily(current.getFamilyId(), now);
                audit.record(new AuditEntry(current.getUserId(), "REFRESH_TOKEN_REUSED", ENTITY,
                        current.getUserId().toString(), Map.of("familyId", current.getFamilyId())));
            }
            // revogado por logout: só uma aba velha, nada a fazer além de recusar
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        User user = users.findById(current.getUserId()).orElseThrow(InvalidRefreshTokenException::new);

        String raw2 = newToken();
        RefreshToken next = current.next(hash(raw2), now, props);
        refreshTokens.save(next);
        current.revoke(now, next.getId());
        return new Session(accessTokens.issue(user), raw2);
    }

    /** Logout: encerra o login inteiro a que o token pertence. Token desconhecido não é erro. */
    @Transactional
    void end(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(raw))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Sai de todos os dispositivos. Access tokens já emitidos valem até vencer (no máximo o TTL do JWT). */
    @Transactional
    int endAll(UUID userId) {
        int revoked = refreshTokens.revokeAllOfUser(userId, clock.instant());
        audit.record(new AuditEntry(userId, "SESSIONS_REVOKED", ENTITY, userId.toString(), Map.of("tokens", revoked)));
        return revoked;
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
