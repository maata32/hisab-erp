package com.minierp.expense.api;

import com.minierp.expense.internal.ExpenseService;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Incomes", description = "Income tracking")
public class IncomeController {

    private final ExpenseService service;

    // ── Income Categories ─────────────────────────────────────────────────────

    @GetMapping("/api/v1/income-categories")
    @PreAuthorize("hasAuthority('expense:read')")
    public List<ExpenseDto.IncomeCategoryResponse> listCategories() {
        return service.listIncomeCategories();
    }

    @PostMapping("/api/v1/income-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('expense:create')")
    public ExpenseDto.IncomeCategoryResponse createCategory(@Valid @RequestBody ExpenseDto.CreateIncomeCategoryRequest req) {
        return service.createIncomeCategory(req.name(), req.parentId());
    }

    // ── Incomes ───────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/incomes")
    @PreAuthorize("hasAuthority('expense:read')")
    public PageResponse<ExpenseDto.IncomeResponse> list(
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.listIncomes(categoryId, pageable));
    }

    @PostMapping("/api/v1/incomes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('expense:create')")
    public ExpenseDto.IncomeResponse create(@Valid @RequestBody ExpenseDto.CreateIncomeRequest req) {
        return service.createIncome(req.categoryId(), req.customerId(), req.amount(),
                req.receivedDate(), req.description(), req.source(), req.paymentMethod());
    }
}
