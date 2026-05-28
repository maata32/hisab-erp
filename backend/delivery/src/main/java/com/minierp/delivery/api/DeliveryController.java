package com.minierp.delivery.api;

import com.minierp.delivery.internal.DeliveryService;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('delivery:create')")
    public DeliveryDto.DeliveryResponse create(@Valid @RequestBody DeliveryDto.CreateDeliveryRequest req) {
        return service.create(req, currentUserId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('delivery:read')")
    public PageResponse<DeliveryDto.DeliveryResponse> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID invoiceId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(customerId, invoiceId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('delivery:read')")
    public DeliveryDto.DeliveryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('delivery:execute')")
    public DeliveryDto.DeliveryResponse start(@PathVariable UUID id) {
        return service.startDelivery(id, currentUserId());
    }

    @PostMapping("/{id}/record")
    @PreAuthorize("hasAuthority('delivery:execute')")
    public DeliveryDto.DeliveryResponse record(@PathVariable UUID id,
                                               @Valid @RequestBody DeliveryDto.RecordDeliveryRequest req) {
        return service.recordDelivery(id, req, currentUserId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('delivery:update')")
    public DeliveryDto.DeliveryResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('delivery:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generatePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"delivery-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private UUID currentUserId() {
        return CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
    }
}
