package com.minierp.customer.api;

import com.minierp.customer.internal.CustomerService;
import com.minierp.shared.security.CurrentUserHolder;
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
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('customer:write')")
    public CustomerDto create(@Valid @RequestBody CreateCustomerRequest req) {
        return service.create(req);
    }

    @GetMapping("/next-code")
    @PreAuthorize("hasAuthority('customer:write')")
    public NextCodeResponse nextCode(@RequestParam(required = false) String type) {
        return new NextCodeResponse(service.suggestCode(type));
    }

    public record NextCodeResponse(String code) {}

    @GetMapping
    @PreAuthorize("hasAuthority('customer:read')")
    public PageResponse<CustomerDto> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:read')")
    public CustomerDto get(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('customer:write')")
    public CustomerDto update(@PathVariable UUID id, @Valid @RequestBody CreateCustomerRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('customer:write')")
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("hasAuthority('customer:read')")
    public CustomerBalanceDto balance(@PathVariable UUID id) {
        return service.getBalanceInfo(id);
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

    /** CDC §15.4 POST /customers/{id}/credits/{cid}/withdraw — manual withdrawal. */
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
}
