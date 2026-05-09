package com.minierp.sales.internal;

import com.minierp.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
@RequiredArgsConstructor
class NumberingService {

    private final DocumentNumberSequenceRepository sequences;

    /**
     * Atomically increments and returns the next document number.
     * Runs in its own transaction so the lock is released immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(DocumentType type) {
        int year = Year.now().getValue();
        var tenantId = TenantContext.require();

        DocumentNumberSequence seq = sequences.lockByTenantTypeYear(tenantId, type, year)
                .orElseGet(() -> sequences.save(DocumentNumberSequence.forType(tenantId, type, year)));
        seq.increment();
        return seq.format();
    }
}
