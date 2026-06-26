package com.minierp.identity.api;

import com.minierp.identity.internal.PlatformAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Cross-tenant super-admin console. Reachable only with a platform token
 * (issued by {@code POST /auth/platform-login}); every method requires SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Platform", description = "Cross-tenant super-admin console")
public class PlatformController {

    private final PlatformAdminService service;

    @GetMapping("/organizations/{orgId}/users")
    public List<UserDto> orgUsers(@PathVariable UUID orgId) {
        return service.listUsers(orgId);
    }

    @GetMapping("/user-counts")
    public List<PlatformDto.OrgUserCount> userCounts() {
        return service.userCounts();
    }

    @PostMapping("/users/{userId}/reset-password")
    public UserDto.ResetPasswordResponse resetPassword(@PathVariable UUID userId) {
        return new UserDto.ResetPasswordResponse(service.resetPassword(userId));
    }

    @PostMapping("/users/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable UUID userId) {
        service.unlock(userId);
    }

    @PostMapping("/users/{userId}/activate")
    public UserDto activate(@PathVariable UUID userId) {
        return service.setActive(userId, true);
    }

    @PostMapping("/users/{userId}/deactivate")
    public UserDto deactivate(@PathVariable UUID userId) {
        return service.setActive(userId, false);
    }
}
