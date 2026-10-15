package com.atlas.identity.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.atlas.identity.CurrentUser;

@RestController
@RequestMapping("/me")
class MeController {

    private final CurrentUser currentUser;
    private final UserRepository users;

    MeController(CurrentUser currentUser, UserRepository users) {
        this.currentUser = currentUser;
        this.users = users;
    }

    @GetMapping
    UserResponse me() {
        return users.findById(currentUser.id())
                .map(UserResponse::from)
                // token válido para um usuário que não existe mais
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
