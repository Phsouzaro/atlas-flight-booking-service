package com.atlas.identity.internal;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * Cadastro e sessão. O access token (JWT, curto) vai no corpo; o refresh token vai só no cookie HttpOnly +
 * SameSite=Strict, que o JavaScript não lê e outro site não envia.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final AuthService auth;
    private final SessionService sessions;
    private final RefreshTokenProperties refresh;

    AuthController(AuthService auth, SessionService sessions, RefreshTokenProperties refresh) {
        this.auth = auth;
        this.sessions = sessions;
        this.refresh = refresh;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withCookie(auth.login(request));
    }

    /** Troca o refresh token do cookie por um par novo (rotação). */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = "${app.security.refresh.cookie-name:atlas_refresh}",
            required = false) String refreshToken) {
        return withCookie(sessions.rotate(refreshToken));
    }

    /** Encerra esta sessão e apaga o cookie. Responde 204 mesmo sem sessão: o resultado é o mesmo. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = "${app.security.refresh.cookie-name:atlas_refresh}",
            required = false) String refreshToken) {
        sessions.end(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    private ResponseEntity<TokenResponse> withCookie(SessionService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), refresh.maxLifetime()).toString())
                .body(session.access());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(refresh.cookieName(), value)
                .httpOnly(true)
                .secure(refresh.cookieSecure())
                .sameSite("Strict")
                .path(refresh.cookiePath())
                .maxAge(maxAge)
                .build();
    }
}
