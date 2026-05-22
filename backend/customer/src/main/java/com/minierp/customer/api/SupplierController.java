package com.minierp.customer.api;

import com.minierp.customer.internal.SupplierService;
import com.minierp.shared.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('supplier:write')")
    public SupplierDto create(@Valid @RequestBody CreateSupplierRequest req) {
        return service.create(req);
    }

    @GetMapping("/next-code")
    @PreAuthorize("hasAuthority('supplier:write')")
    public NextCodeResponse nextCode() {
        return new NextCodeResponse(service.suggestCode());
    }

    public record NextCodeResponse(String code) {}

    @GetMapping
    @PreAuthorize("hasAuthority('supplier:read')")
    public PageResponse<SupplierDto> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:read')")
    public SupplierDto get(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:write')")
    public SupplierDto update(@PathVariable UUID id, @Valid @RequestBody CreateSupplierRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('supplier:write')")
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAuthority('supplier:read')")
    public SupplierBalanceDto balance(@PathVariable UUID id) {
        return service.getBalanceInfo(id);
    }
}
