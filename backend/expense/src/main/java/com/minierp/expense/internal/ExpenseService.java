package com.minierp.expense.internal;

import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.expense.api.ExpenseDto;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final ExpenseCategoryRepository categories;
    private final IncomeCategoryRepository incomeCategories;
    private final IncomeRepository incomes;
    private final AttachmentStorageService storage;
    private final DocumentRenderer documentRenderer;

    // ── Categories ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExpenseDto.CategoryResponse> listCategories() {
        return categories.findByActiveTrueOrderByCreatedAtDesc().stream().map(this::toCategoryDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto.CategoryResponse> listAllCategories() {
        return categories.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream().map(this::toCategoryDto).toList();
    }

    @Transactional
    public ExpenseDto.CategoryResponse createCategory(String name, UUID parentId, String color) {
        return toCategoryDto(categories.save(ExpenseCategory.builder()
                .name(name).parentId(parentId).color(color).build()));
    }

    @Transactional
    public ExpenseDto.CategoryResponse updateCategory(UUID id, ExpenseDto.UpdateCategoryRequest req) {
        ExpenseCategory cat = categories.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.expense_category", id));
        cat.setName(req.name());
        cat.setParentId(req.parentId());
        cat.setColor(req.color());
        if (req.active() != null) cat.setActive(req.active());
        return toCategoryDto(cat);
    }

    @Transactional
    public void deactivateCategory(UUID id) {
        ExpenseCategory cat = categories.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.expense_category", id));
        cat.setActive(false);
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto.IncomeCategoryResponse> listIncomeCategories() {
        return incomeCategories.findByActiveTrue().stream().map(this::toIncomeCategoryDto).toList();
    }

    @Transactional
    public ExpenseDto.IncomeCategoryResponse createIncomeCategory(String name, UUID parentId) {
        return toIncomeCategoryDto(incomeCategories.save(IncomeCategory.builder()
                .name(name).parentId(parentId).build()));
    }

    // ── Expenses ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ExpenseDto.ExpenseResponse> list(UUID categoryId, Pageable pageable) {
        Page<Expense> page = categoryId != null
                ? expenses.findByCategoryId(categoryId, pageable)
                : expenses.findAll(pageable);
        return page.map(this::toExpenseDto);
    }

    @Transactional(readOnly = true)
    public ExpenseDto.ExpenseResponse get(UUID id) {
        return toExpenseDto(getEntity(id));
    }

    @Transactional
    public ExpenseDto.ExpenseResponse create(UUID categoryId, UUID supplierId, BigDecimal amount,
                                             LocalDate expenseDate, String description,
                                             String paymentMethod, String recurrenceRule, String notes) {
        String number = generateExpenseNumber();
        ExpensePaymentMethod method = paymentMethod != null
                ? ExpensePaymentMethod.valueOf(paymentMethod) : ExpensePaymentMethod.UNPAID;

        LocalDate nextRecurrence = null;
        if (recurrenceRule != null && !recurrenceRule.isBlank()) {
            nextRecurrence = computeNextRecurrence(expenseDate != null ? expenseDate : LocalDate.now(), recurrenceRule);
        }

        Expense expense = Expense.builder()
                .expenseNumber(number)
                .categoryId(categoryId)
                .partyId(supplierId)
                .amount(amount)
                .expenseDate(expenseDate != null ? expenseDate : LocalDate.now())
                .description(description)
                .paymentMethod(method)
                .balance(amount)
                .recurring(recurrenceRule != null && !recurrenceRule.isBlank())
                .recurrenceRule(recurrenceRule)
                .nextRecurrenceDate(nextRecurrence)
                .build();
        return toExpenseDto(expenses.save(expense));
    }

    @Transactional
    public String uploadAttachment(UUID expenseId, MultipartFile file) {
        Expense expense = getEntity(expenseId);
        String url = storage.upload("expenses/" + expenseId, file);
        List<String> attachments = expense.getAttachments() != null
                ? new ArrayList<>(expense.getAttachments()) : new ArrayList<>();
        attachments.add(url);
        expense.setAttachments(attachments);
        return url;
    }

    @Transactional
    public ExpenseDto.ExpenseResponse approve(UUID id) {
        Expense expense = getEntity(id);
        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        expense.setApprovalStatus(ApprovalStatus.APPROVED);
        expense.setApprovedBy(userId);
        expense.setApprovedAt(Instant.now());
        return toExpenseDto(expense);
    }

    @Transactional
    public ExpenseDto.ExpenseResponse reject(UUID id) {
        Expense expense = getEntity(id);
        expense.setApprovalStatus(ApprovalStatus.REJECTED);
        return toExpenseDto(expense);
    }

    /** CDC §4.1 — expense voucher PDF (justificatif de dépense). */
    @Transactional(readOnly = true)
    public byte[] generateReceiptPdf(UUID expenseId) {
        Expense e = expenses.findById(expenseId)
                .orElseThrow(() -> NotFoundException.of("entity.expense", expenseId));
        ExpenseCategory cat = categories.findById(e.getCategoryId()).orElse(null);

        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("expenseNumber", e.getExpenseNumber());
        vars.put("expenseDate", e.getExpenseDate());
        vars.put("amount", e.getAmount());
        vars.put("paidAmount", e.getPaidAmount());
        vars.put("currency", "MRU");
        vars.put("category", cat == null ? "—" : cat.getName());
        vars.put("supplierName", null);
        vars.put("description", e.getDescription() == null ? "" : e.getDescription());
        vars.put("paymentMethod", e.getPaymentMethod().name());
        vars.put("paymentStatus", e.getPaymentStatus().name());
        vars.put("approvalStatus", e.getApprovalStatus().name());
        vars.put("approvedAt", e.getApprovedAt());
        return documentRenderer.renderPdf(PdfRenderRequest.of("expense-receipt", vars));
    }

    // ── Incomes ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ExpenseDto.IncomeResponse> listIncomes(UUID categoryId, Pageable pageable) {
        Page<Income> page = categoryId != null
                ? incomes.findByCategoryId(categoryId, pageable)
                : incomes.findAll(pageable);
        return page.map(this::toIncomeDto);
    }

    @Transactional
    public ExpenseDto.IncomeResponse createIncome(UUID categoryId, UUID customerId, BigDecimal amount,
                                                   LocalDate receivedDate, String description,
                                                   String source, String paymentMethod) {
        int year = Year.now().getValue();
        long seq = incomes.count() + 1;
        String number = String.format("INC-%d-%05d", year, seq);

        return toIncomeDto(incomes.save(Income.builder()
                .incomeNumber(number)
                .categoryId(categoryId)
                .partyId(customerId)
                .amount(amount)
                .receivedDate(receivedDate != null ? receivedDate : LocalDate.now())
                .description(description)
                .source(source)
                .paymentMethod(paymentMethod)
                .build()));
    }

    // ── Recurrence job (daily) ────────────────────────────────────────────────

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void generateRecurringExpenses() {
        expenses.findDueRecurrences(LocalDate.now()).forEach(parent -> {
            Expense child = Expense.builder()
                    .expenseNumber(generateExpenseNumber())
                    .categoryId(parent.getCategoryId())
                    .partyId(parent.getPartyId())
                    .amount(parent.getAmount())
                    .expenseDate(parent.getNextRecurrenceDate())
                    .description(parent.getDescription())
                    .paymentMethod(parent.getPaymentMethod())
                    .balance(parent.getAmount())
                    .recurring(true)
                    .recurrenceRule(parent.getRecurrenceRule())
                    .parentRecurrenceId(parent.getId())
                    .nextRecurrenceDate(computeNextRecurrence(
                            parent.getNextRecurrenceDate(), parent.getRecurrenceRule()))
                    .build();
            expenses.save(child);
            parent.setNextRecurrenceDate(child.getNextRecurrenceDate());
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Expense getEntity(UUID id) {
        return expenses.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.expense", id));
    }

    private String generateExpenseNumber() {
        int year = Year.now().getValue();
        long seq = expenses.count() + 1;
        return String.format("EXP-%d-%05d", year, seq);
    }

    private LocalDate computeNextRecurrence(LocalDate from, String rule) {
        if (rule == null) return null;
        return switch (rule.toUpperCase()) {
            case "DAILY"     -> from.plusDays(1);
            case "WEEKLY"    -> from.plusWeeks(1);
            case "MONTHLY"   -> from.plusMonths(1);
            case "QUARTERLY" -> from.plusMonths(3);
            case "YEARLY"    -> from.plusYears(1);
            default          -> from.plusMonths(1);
        };
    }

    private ExpenseDto.CategoryResponse toCategoryDto(ExpenseCategory c) {
        return new ExpenseDto.CategoryResponse(c.getId(), c.getName(), c.getParentId(), c.getColor(), c.isActive());
    }

    private ExpenseDto.IncomeCategoryResponse toIncomeCategoryDto(IncomeCategory c) {
        return new ExpenseDto.IncomeCategoryResponse(c.getId(), c.getName(), c.getParentId(), c.isActive());
    }

    private ExpenseDto.ExpenseResponse toExpenseDto(Expense e) {
        return new ExpenseDto.ExpenseResponse(
                e.getId(), e.getExpenseNumber(), e.getCategoryId(), e.getPartyId(),
                e.getAmount(), e.getExpenseDate(), e.getDescription(),
                e.getPaymentMethod() != null ? e.getPaymentMethod().name() : null,
                e.getPaymentStatus() != null ? e.getPaymentStatus().name() : null,
                e.getPaidAmount(), e.getBalance(), e.getAttachments(),
                e.isRecurring(), e.getRecurrenceRule(), e.getNextRecurrenceDate(),
                e.getParentRecurrenceId(),
                e.getApprovalStatus() != null ? e.getApprovalStatus().name() : null,
                e.getApprovedBy(), e.getApprovedAt());
    }

    private ExpenseDto.IncomeResponse toIncomeDto(Income i) {
        return new ExpenseDto.IncomeResponse(
                i.getId(), i.getIncomeNumber(), i.getCategoryId(), i.getPartyId(),
                i.getAmount(), i.getReceivedDate(), i.getDescription(),
                i.getSource(), i.getPaymentMethod(), i.getAttachments());
    }
}
