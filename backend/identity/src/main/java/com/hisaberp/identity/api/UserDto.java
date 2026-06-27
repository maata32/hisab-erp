package com.hisaberp.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String fullName,
        String phone,
        String preferredLanguage,
        boolean active,
        Instant lastLoginAt,
        boolean locked,
        List<String> roleCodes
) {

    public record CreateRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 1, max = 200) String fullName,
            @Size(max = 30) String phone,
            @Size(max = 5) String preferredLanguage,
            @NotBlank @Size(min = 8, max = 200) String password,
            List<String> roleCodes
    ) {}

    public record UpdateRequest(
            @Email @Size(max = 255) String email,
            @Size(max = 200) String fullName,
            @Size(max = 30) String phone,
            @Size(max = 5) String preferredLanguage,
            Boolean active,
            List<String> roleCodes
    ) {}

    public record ResetPasswordResponse(String temporaryPassword) {}
}
