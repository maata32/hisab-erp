package com.minierp.expense.api;

import com.minierp.expense.internal.ExpenseService;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Expense management")
public class ExpenseController {

    private final ExpenseService service;

    // ── Categories ──────────────────────────────────────────────────────────

    @GetMapping("/api/v1/expense-categories")
    @PreAuthorize("hasAuthority('expense:read')")
    public List<ExpenseDto.CategoryResponse> listCategories(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return includeInactive ? service.listAllCategories() : service.listCategories();
    }

    @PostMapping("/api/v1/expense-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('expense:write')")
    public ExpenseDto.CategoryResponse createCategory(@Valid @RequestBody ExpenseDto.CreateCategoryRequest req) {
        return service.createCategory(req.name(), req.parentId(), req.color());
    }

    @PutMapping("/api/v1/expense-categories/{id}")
    @PreAuthorize("hasAuthority('expense:write')")
    public ExpenseDto.CategoryResponse updateCategory(@PathVariable UUID id,
                                                     @Valid @RequestBody ExpenseDto.UpdateCategoryRequest req) {
        return service.updateCategory(id, req);
    }

    @DeleteMapping("/api/v1/expense-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('expense:write')")
    public void deactivateCategory(@PathVariable UUID id) {
        service.deactivateCategory(id);
    }

    // ── Expenses ─────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/expenses")
    @PreAuthorize("hasAuthority('expense:read')")
    public PageResponse<ExpenseDto.ExpenseResponse> list(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(categoryId, pageable));
    }

    @GetMapping("/api/v1/expenses/{id}")
    @PreAuthorize("hasAuthority('expense:read')")
    public ExpenseDto.ExpenseResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/api/v1/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('expense:write')")
    public ExpenseDto.ExpenseResponse create(@Valid @RequestBody ExpenseDto.CreateExpenseRequest req) {
        return service.create(req.categoryId(), req.supplierId(), req.amount(),
                req.expenseDate(), req.description(), req.paymentMethod(),
                req.recurrenceRule(), req.notes());
    }

    @PostMapping(value = "/api/v1/expenses/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('expense:write')")
    public Map<String, String> uploadAttachment(@PathVariable UUID id,
                                                @RequestParam("file") MultipartFile file) {
        String url = service.uploadAttachment(id, file);
        return Map.of("url", url);
    }

    @PostMapping("/api/v1/expenses/{id}/approve")
    @PreAuthorize("hasAuthority('expense:write')")
    public ExpenseDto.ExpenseResponse approve(@PathVariable UUID id) {
        return service.approve(id);
    }

    @PostMapping("/api/v1/expenses/{id}/reject")
    @PreAuthorize("hasAuthority('expense:write')")
    public ExpenseDto.ExpenseResponse reject(@PathVariable UUID id) {
        return service.reject(id);
    }

    /** CDC §4.1 — expense voucher PDF. */
    @GetMapping("/api/v1/expenses/{id}/receipt.pdf")
    @PreAuthorize("hasAuthority('expense:read')")
    public org.springframework.http.ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        byte[] pdf = service.generateReceiptPdf(id);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"expense-" + id + ".pdf\"")
                .body(pdf);
    }
}
