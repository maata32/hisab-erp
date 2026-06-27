package com.hisaberp.partner.internal;

import com.hisaberp.partner.api.ArBalanceOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ArBalanceService implements ArBalanceOperations {

    private final ArBalanceRepository arBalances;

    @Override
    @Transactional
    public void addToInvoiced(UUID customerId, BigDecimal amount) {
        ArBalance b = getOrCreate(customerId);
        b.setTotalInvoiced(b.getTotalInvoiced().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        arBalances.save(b);
    }

    @Override
    @Transactional
    public void addToPaid(UUID customerId, BigDecimal amount, boolean isLastPaymentToday) {
        ArBalance b = arBalances.lockByPartyId(customerId).orElseGet(() -> getOrCreate(customerId));
        b.setTotalPaid(b.getTotalPaid().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        if (isLastPaymentToday) b.setLastPaymentDate(LocalDate.now());
        arBalances.save(b);
    }

    @Override
    @Transactional
    public void addToOverdue(UUID customerId, BigDecimal amount) {
        ArBalance b = getOrCreate(customerId);
        b.setOverdueAmount(b.getOverdueAmount().add(amount));
        arBalances.save(b);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalanceAmount(UUID customerId) {
        return arBalances.findByPartyId(customerId)
                .map(ArBalance::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private ArBalance getOrCreate(UUID partyId) {
        return arBalances.findByPartyId(partyId)
                .orElse(ArBalance.builder().partyId(partyId).build());
    }
}
