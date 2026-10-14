package com.flightsai.identity.internal;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // 72 bytes é o limite do BCrypt
        @NotBlank @Size(min = 8, max = 72) String password) {
}

record LoginRequest(@NotBlank String email, @NotBlank String password) {
}

record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}

record UserResponse(UUID id, String email, String role) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }
}
