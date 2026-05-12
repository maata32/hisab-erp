package com.minierp.identity.api;

import com.minierp.identity.internal.UserService;
import com.minierp.shared.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public PageResponse<UserDto> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public UserDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:create')")
    public UserDto create(@Valid @RequestBody UserDto.CreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UserDto.UpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('user:delete')")
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:update')")
    public UserDto.ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return new UserDto.ResetPasswordResponse(service.resetPassword(id));
    }

    @PostMapping("/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('user:update')")
    public void unlock(@PathVariable UUID id) {
        service.unlock(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('user:read') or hasAuthority('role:read')")
    public List<RoleDto> roles() {
        return service.listRoles();
    }
}
