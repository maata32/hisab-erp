package com.minierp.delivery.internal;

import com.minierp.customer.api.CustomerLookup;
import com.minierp.customer.api.CustomerSummary;
import com.minierp.delivery.api.DeliveryDto;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveries;
    private final DeliveryLineRepository deliveryLines;
    private final CustomerLookup customerLookup;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;

    @Transactional
    public DeliveryDto.DeliveryResponse create(DeliveryDto.CreateDeliveryRequest req, UUID userId) {
        customerLookup.findById(req.customerId())
                .orElseThrow(() -> NotFoundException.of("entity.customer", req.customerId()));
        String number = numbering.nextDeliveryNumber();
        Delivery delivery = Delivery.builder()
                .number(number)
                .customerId(req.customerId())
                .orderId(req.orderId())
                .scheduledDate(req.scheduledDate())
                .address(req.address())
                .contactPhone(req.contactPhone())
                .notes(req.notes())
                .build();
        deliveries.save(delivery);

        if (req.lines() != null) {
            for (DeliveryDto.LineRequest lr : req.lines()) {
                deliveryLines.save(DeliveryLine.builder()
                        .deliveryId(delivery.getId())
                        .productId(lr.productId())
                        .uomId(lr.uomId())
                        .quantityOrdered(lr.quantityOrdered())
                        .snapshotName(lr.productName())
                        .snapshotSku(lr.sku())
                        .build());
            }
        }
        return toDto(delivery);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse startDelivery(UUID id, UUID userId) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        if (d.getStatus() != DeliveryStatus.PENDING) {
            throw new BusinessException("error.delivery.not_pending", Map.of("status", d.getStatus()));
        }
        d.setStatus(DeliveryStatus.IN_PROGRESS);
        return toDto(d);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse recordDelivery(UUID id, DeliveryDto.RecordDeliveryRequest req, UUID userId) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        if (d.getStatus() == DeliveryStatus.DELIVERED || d.getStatus() == DeliveryStatus.CANCELLED) {
            throw new BusinessException("error.delivery.already_terminal", Map.of("status", d.getStatus()));
        }

        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(id);

        for (DeliveryDto.LineDelivered ld : req.lines()) {
            lines.stream().filter(l -> l.getId().equals(ld.lineId())).findFirst()
                    .ifPresent(line -> {
                        line.setQuantityDelivered(line.getQuantityDelivered().add(ld.quantityDelivered()));
                        if (line.getQuantityDelivered().compareTo(line.getQuantityOrdered()) >= 0) {
                            line.setStatus(DeliveryLineStatus.COMPLETED);
                        } else {
                            line.setStatus(DeliveryLineStatus.PARTIAL);
                        }
                    });
        }

        boolean allComplete = lines.stream().allMatch(l -> l.getStatus() == DeliveryLineStatus.COMPLETED);
        boolean anyComplete = lines.stream().anyMatch(l -> l.getStatus() != DeliveryLineStatus.PENDING);

        if (allComplete) {
            d.setStatus(DeliveryStatus.DELIVERED);
            d.setDeliveredAt(Instant.now());
            d.setDeliveredBy(userId);
        } else if (anyComplete) {
            d.setStatus(DeliveryStatus.PARTIAL);
        }

        if (req.signedBy() != null) d.setSignedBy(req.signedBy());
        if (req.notes() != null) d.setNotes(req.notes());

        return toDto(d);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse cancel(UUID id) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        if (d.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessException("error.delivery.already_delivered", Map.of());
        }
        d.setStatus(DeliveryStatus.CANCELLED);
        return toDto(d);
    }

    @Transactional(readOnly = true)
    public DeliveryDto.DeliveryResponse get(UUID id) {
        return toDto(deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryDto.DeliveryResponse> list(UUID customerId, Pageable pageable) {
        var page = customerId != null
                ? deliveries.findByCustomerId(customerId, pageable)
                : deliveries.findAll(pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID id) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        CustomerSummary customer = customerLookup.findById(d.getCustomerId()).orElse(null);
        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(id);
        Map<String, Object> vars = buildPdfVars(d, lines, customer);
        return renderer.renderPdf(PdfRenderRequest.of("delivery-note", vars));
    }

    private DeliveryDto.DeliveryResponse toDto(Delivery d) {
        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(d.getId());
        String customerName = customerLookup.findById(d.getCustomerId()).map(CustomerSummary::name).orElse("");
        return new DeliveryDto.DeliveryResponse(
                d.getId(), d.getNumber(), d.getCustomerId(), customerName, d.getOrderId(),
                d.getStatus().name(), d.getScheduledDate(), d.getDeliveredAt(),
                d.getAddress(), d.getContactPhone(), d.getSignedBy(), d.getNotes(),
                lines.stream().map(l -> new DeliveryDto.LineDto(l.getId(), l.getProductId(), l.getUomId(),
                        l.getQuantityOrdered(), l.getQuantityDelivered(), l.getStatus().name(),
                        l.getSnapshotName(), l.getSnapshotSku())).toList(),
                d.getCreatedAt());
    }

    private Map<String, Object> buildPdfVars(Delivery d, List<DeliveryLine> lines, CustomerSummary customer) {
        record LineModel(String productName, String sku, BigDecimal quantityOrdered, BigDecimal quantityDelivered) {}
        record DeliveryModel(String number, java.time.LocalDate scheduledDate, java.time.Instant deliveredAt,
                             String address, String contactPhone, String signedBy, String notes,
                             List<LineModel> lines) {}
        record CustomerModel(String name, String address, String phone) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantityOrdered(), l.getQuantityDelivered())).toList();
        return Map.of(
                "delivery", new DeliveryModel(d.getNumber(), d.getScheduledDate(), d.getDeliveredAt(),
                        d.getAddress(), d.getContactPhone(), d.getSignedBy(), d.getNotes(), lm),
                "customer", new CustomerModel(customer != null ? customer.name() : "", "", customer != null ? customer.phone() : ""),
                "orgName", "Mini-ERP", "orgAddress", "", "logoUrl", ""
        );
    }
}
