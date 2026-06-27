package com.hisaberp.sales.internal;

import com.hisaberp.shared.tenant.TenantContext;
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

        sequences.insertIfAbsent(tenantId, type.name(), year, DocumentNumberSequence.prefixFor(type));
        DocumentNumberSequence seq = sequences.lockByTenantTypeYear(tenantId, type, year).orElseThrow();
        seq.increment();
        return seq.format();
    }
}
