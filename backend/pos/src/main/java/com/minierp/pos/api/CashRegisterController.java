package com.minierp.pos.api;

import com.minierp.pos.internal.PosService;
import com.minierp.pos.internal.PosService.CreateRegisterRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos/registers")
@RequiredArgsConstructor
@Tag(name = "POS Registers")
public class CashRegisterController {

    private final PosService posService;

    @GetMapping
    @PreAuthorize("hasAuthority('pos:read')")
    public List<CashRegisterDto> list() {
        return posService.listRegisters();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pos:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public CashRegisterDto create(@Valid @RequestBody CreateRequest req) {
        return posService.createRegister(new CreateRegisterRequest(
                req.code(), req.name(), req.warehouseId(), req.defaultPriceTierId(), req.receiptWidthMm()));
    }

    public record CreateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull UUID warehouseId,
            UUID defaultPriceTierId,
            Integer receiptWidthMm) {}
}
