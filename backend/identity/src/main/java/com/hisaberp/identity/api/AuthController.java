package com.hisaberp.identity.api;

import com.hisaberp.identity.internal.AuthService;
import com.hisaberp.shared.security.CurrentUser;
import com.hisaberp.shared.security.CurrentUserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, refresh, logout, current user")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Sign in with email + password (within a tenant)",
            description = "Tenant is identified by the URL host or the X-Tenant-Code header.")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/platform-login")
    @Operation(summary = "Platform (super-admin) sign in — email + password, no tenant code",
            description = "Issues a cross-tenant token (no tenant binding) limited to SUPER_ADMIN endpoints.")
    public LoginResponse platformLogin(@Valid @RequestBody PlatformLoginRequest req) {
        return authService.platformLogin(req);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Trade a refresh token for a new access token + rotated refresh token")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the current refresh token (called from frontend on logout)")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the current user's profile + permissions")
    public CurrentUser me() {
        return CurrentUserHolder.require();
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 1, max = 200) String password,
            @Size(max = 50) String tenantCode
    ) {}

    public record PlatformLoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 1, max = 200) String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            long accessTokenExpiresInSeconds,
            CurrentUser user
    ) {}
}
