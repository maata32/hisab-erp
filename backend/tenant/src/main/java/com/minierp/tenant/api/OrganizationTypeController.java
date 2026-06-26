package com.minierp.tenant.api;

import com.minierp.tenant.internal.OrganizationTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Super-admin CRUD for the configurable organization types. */
@RestController
@RequestMapping("/api/v1/organization-types")
@RequiredArgsConstructor
@Tag(name = "Organization types", description = "Configurable tenant types")
public class OrganizationTypeController {

    private final OrganizationTypeService service;

    /** Public read — used by the registration form and the super-admin console. */
    @GetMapping
    public List<OrganizationTypeDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public OrganizationTypeDto create(@Valid @RequestBody OrganizationTypeDto.CreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public OrganizationTypeDto update(@PathVariable UUID id, @Valid @RequestBody OrganizationTypeDto.UpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
