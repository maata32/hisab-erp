package com.minierp.partner.internal;

import com.minierp.partner.api.ApBalanceOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ApBalanceService implements ApBalanceOperations {

    private final ApBalanceRepository apBalances;

    @Override
    @Transactional
    public void addToInvoiced(UUID supplierId, BigDecimal amount) {
        ApBalance b = getOrCreate(supplierId);
        b.setTotalInvoiced(b.getTotalInvoiced().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        apBalances.save(b);
    }

    @Override
    @Transactional
    public void addToPaid(UUID supplierId, BigDecimal amount, boolean isLastPaymentToday) {
        ApBalance b = apBalances.lockByPartyId(supplierId).orElseGet(() -> getOrCreate(supplierId));
        b.setTotalPaid(b.getTotalPaid().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        if (isLastPaymentToday) b.setLastPaymentDate(LocalDate.now());
        apBalances.save(b);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalanceAmount(UUID supplierId) {
        return apBalances.findByPartyId(supplierId)
                .map(ApBalance::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public ApSnapshot getApSnapshot(UUID supplierId) {
        return apBalances.findByPartyId(supplierId)
                .map(b -> new ApSnapshot(b.getTotalInvoiced(), b.getTotalPaid(),
                        b.getBalance(), b.getLastPaymentDate()))
                .orElse(new ApSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    private ApBalance getOrCreate(UUID partyId) {
        return apBalances.findByPartyId(partyId)
                .orElse(ApBalance.builder().partyId(partyId).build());
    }
}
