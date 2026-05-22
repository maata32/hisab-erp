package com.minierp.partner.api;

import com.minierp.partner.internal.PartnerService;
import com.minierp.shared.util.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:write') or hasAuthority('supplier:write')")
    public PartnerDto create(@Valid @RequestBody CreatePartnerRequest req) {
        return service.create(req);
    }

    /**
     * Suggests the next code for a given role. {@code role=customer} returns a
     * 411xxx-style code; {@code role=supplier} returns a 401xxx-style code.
     */
    @GetMapping("/next-code")
    @PreAuthorize("hasAuthority('customer:write') or hasAuthority('supplier:write')")
    public NextCodeResponse nextCode(@RequestParam String role,
                                    @RequestParam(required = false) String type) {
        return new NextCodeResponse(service.suggestCode(role, type));
    }

    public record NextCodeResponse(String code) {}

    /**
     * Paginated partner list. {@code role=CUSTOMER} or {@code role=SUPPLIER}
     * filters by role; omitted returns all active partners.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('customer:read') or hasAuthority('supplier:read')")
    public PageResponse<PartnerDto> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(role, q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:read') or hasAuthority('supplier:read')")
    public PartnerDto get(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:write') or hasAuthority('supplier:write')")
    public PartnerDto update(@PathVariable UUID id, @Valid @RequestBody CreatePartnerRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('customer:write') or hasAuthority('supplier:write')")
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @GetMapping("/{id}/ar-balance")
    @PreAuthorize("hasAuthority('customer:read')")
    public ArBalanceDto arBalance(@PathVariable UUID id) {
        return service.getArBalance(id);
    }

    @GetMapping("/{id}/ap-balance")
    @PreAuthorize("hasAuthority('supplier:read')")
    public ApBalanceDto apBalance(@PathVariable UUID id) {
        return service.getApBalance(id);
    }

    @GetMapping("/{id}/credits")
    @PreAuthorize("hasAuthority('customer:read')")
    public List<CustomerCreditDto> credits(@PathVariable UUID id) {
        return service.listCredits(id);
    }

    @PostMapping("/{id}/credits")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:write')")
    public CustomerCreditDto createCredit(@PathVariable UUID id,
                                          @Valid @RequestBody CreateCreditRequest req) {
        return service.createCredit(id, req.amount(), req.source(), req.notes());
    }

    @PostMapping("/{id}/credits/{cid}/withdraw")
    @PreAuthorize("hasAuthority('customer:write')")
    public CustomerCreditDto withdrawCredit(@PathVariable UUID id,
                                            @PathVariable UUID cid,
                                            @Valid @RequestBody WithdrawCreditRequest req) {
        return service.withdrawCredit(id, cid, req.amount(), req.paymentId(), req.notes());
    }

    public record WithdrawCreditRequest(
            @NotNull @Positive BigDecimal amount,
            UUID paymentId,
            String notes) {}

    @PostMapping("/{id}/activate-supplier-role")
    @PreAuthorize("hasAuthority('supplier:write')")
    public PartnerDto activateSupplierRole(@PathVariable UUID id,
                                           @Valid @RequestBody ActivateSupplierRoleRequest req) {
        return service.activateSupplierRole(id, req);
    }

    @PostMapping("/{id}/activate-customer-role")
    @PreAuthorize("hasAuthority('customer:write')")
    public PartnerDto activateCustomerRole(@PathVariable UUID id,
                                           @Valid @RequestBody ActivateCustomerRoleRequest req) {
        return service.activateCustomerRole(id, req);
    }
}
