package com.atlas.identity.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.atlas.identity.CurrentUser;

@RestController
@RequestMapping("/me")
class MeController {

    private final CurrentUser currentUser;
    private final UserRepository users;
    private final SessionService sessions;

    MeController(CurrentUser currentUser, UserRepository users, SessionService sessions) {
        this.currentUser = currentUser;
        this.users = users;
        this.sessions = sessions;
    }

    @GetMapping
    UserResponse me() {
        return users.findById(currentUser.id())
                .map(UserResponse::from)
                // token válido para um usuário que não existe mais
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /**
     * Sai de todos os dispositivos: revoga todos os refresh tokens do usuário. Access tokens já emitidos valem
     * até vencer (no máximo o TTL do JWT, 15 min).
     */
    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void endAllSessions() {
        sessions.endAll(currentUser.id());
    }
}
