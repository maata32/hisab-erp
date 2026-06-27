package com.hisaberp.tenant.internal;

import com.hisaberp.shared.error.ConflictException;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.error.ValidationException;
import com.hisaberp.tenant.api.SubscriptionPlanDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super-admin CRUD for subscription plans (formules). Global reference data. Conventions
 * mirror the org-type CRUD: code is immutable, and a plan still chosen by an organization
 * cannot be deleted (deactivate it instead). Null limits mean "unlimited".
 */
@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository plans;
    private final OrganizationRepository orgs;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> listAll() {
        return plans.findAll().stream()
                .sorted(Comparator.comparing(SubscriptionPlan::getMonthlyPrice))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SubscriptionPlanDto create(SubscriptionPlanDto.CreateRequest req) {
        String code = req.code().trim().toUpperCase();
        if (plans.findByCode(code).isPresent()) {
            throw new ConflictException("error.data_integrity", Map.of("field", "code", "value", code));
        }
        SubscriptionPlan p = SubscriptionPlan.builder()
                .code(code)
                .name(req.name().trim())
                .monthlyPrice(req.monthlyPrice())
                .annualPrice(req.annualPrice())
                .maxCashRegisters(req.maxCashRegisters())
                .maxUsers(req.maxUsers())
                .maxProducts(req.maxProducts())
                .maxProductImages(req.maxProductImages())
                .active(true)
                .build();
        return toDto(plans.save(p));
    }

    @Transactional
    public SubscriptionPlanDto update(UUID id, SubscriptionPlanDto.UpdateRequest req) {
        SubscriptionPlan p = load(id);
        // Code is immutable. Limits are full-replace (null = unlimited); name/price/active are guarded.
        if (req.name() != null && !req.name().isBlank()) p.setName(req.name().trim());
        if (req.monthlyPrice() != null) p.setMonthlyPrice(req.monthlyPrice());
        p.setAnnualPrice(req.annualPrice());
        p.setMaxCashRegisters(req.maxCashRegisters());
        p.setMaxUsers(req.maxUsers());
        p.setMaxProducts(req.maxProducts());
        p.setMaxProductImages(req.maxProductImages());
        if (req.active() != null) p.setActive(req.active());
        return toDto(p);
    }

    @Transactional
    public void delete(UUID id) {
        SubscriptionPlan p = load(id);
        long inUse = orgs.countBySubscriptionPlanId(id);
        if (inUse > 0) {
            throw new ValidationException("subscription_plan.in_use", Map.of("count", inUse));
        }
        plans.delete(p);
    }

    private SubscriptionPlan load(UUID id) {
        return plans.findById(id).orElseThrow(() -> NotFoundException.of("entity.subscription_plan", id));
    }

    private SubscriptionPlanDto toDto(SubscriptionPlan p) {
        return new SubscriptionPlanDto(
                p.getId(), p.getCode(), p.getName(),
                p.getMonthlyPrice(), p.getAnnualPrice(),
                p.getMaxCashRegisters(), p.getMaxUsers(), p.getMaxProducts(), p.getMaxProductImages(),
                p.isActive());
    }
}
