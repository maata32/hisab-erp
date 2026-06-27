package com.hisaberp.pos.api;

import java.util.List;
import java.util.UUID;

public record SyncSalesResponse(List<SyncResult> results) {

    public record SyncResult(
            String idempotencyKey,
            UUID saleId,
            String number,
            String status,
            String error) {}
}
