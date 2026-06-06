package com.minierp.expense;

import com.minierp.MiniErpApplication;
import com.minierp.expense.api.ExpenseDto;
import com.minierp.expense.internal.ExpenseService;
import com.minierp.payment.api.PaymentDto;
import com.minierp.payment.internal.PaymentService;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.tenant.TenantContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Expense approval caps (per-category, cumulative day/month) + settlement of an
 * expense through a party-less CASH_OUT payment allocated to the EXPENSE target.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Expense — per-category cap approval + payment-module settlement")
class ExpenseSettlementApprovalIT {

    @Autowired ExpenseService expenseService;
    @Autowired PaymentService paymentService;
    @Autowired JdbcTemplate jdbc;

    @PersistenceContext EntityManager em;

    UUID tenantId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Expense Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "exptest-" + tenantId);

        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    }

    @Test
    @DisplayName("Cumulative category spend over the daily cap flips the expense to PENDING")
    void dailyCapTriggersPendingApproval() {
        UUID catId = expenseService.createCategory(
                "Daily-capped", null, null, new BigDecimal("1000.00"), null).id();

        // First expense within the cap → NOT_REQUIRED.
        ExpenseDto.ExpenseResponse first = expenseService.create(
                catId, null, new BigDecimal("600.00"), LocalDate.now(), null, "UNPAID", null, null);
        assertThat(first.approvalStatus()).isEqualTo("NOT_REQUIRED");

        // Second expense pushes the day-to-date total (600 + 600 = 1200) over 1000 → PENDING.
        ExpenseDto.ExpenseResponse second = expenseService.create(
                catId, null, new BigDecimal("600.00"), LocalDate.now(), null, "UNPAID", null, null);
        assertThat(second.approvalStatus()).isEqualTo("PENDING");

        // Approval is now reachable and stamps the status.
        assertThat(expenseService.approve(second.id()).approvalStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Category with no caps never requires approval")
    void noCapNeverRequiresApproval() {
        UUID catId = expenseService.createCategory("Uncapped", null, null, null, null).id();
        ExpenseDto.ExpenseResponse e = expenseService.create(
                catId, null, new BigDecimal("99999.00"), LocalDate.now(), null, "UNPAID", null, null);
        assertThat(e.approvalStatus()).isEqualTo("NOT_REQUIRED");
    }

    @Test
    @DisplayName("A party-less CASH_OUT allocated to the expense settles it to PAID on confirm")
    void payExpenseThroughPaymentModule() {
        UUID catId = expenseService.createCategory("Settle", null, null, null, null).id();
        ExpenseDto.ExpenseResponse e = expenseService.create(
                catId, null, new BigDecimal("500.00"), LocalDate.now(), null, "UNPAID", null, null);
        assertThat(e.paymentStatus()).isEqualTo("UNPAID");

        PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                "CASH_OUT", null, new BigDecimal("500.00"), "MRU", LocalDate.now(), "CASH",
                null, null, null,
                List.of(new PaymentDto.AllocationRequest("EXPENSE", e.id(), new BigDecimal("500.00"), null))));
        assertThat(pay.partyId()).isNull();

        paymentService.confirm(pay.id(), null);

        ExpenseDto.ExpenseResponse settled = expenseService.get(e.id());
        assertThat(settled.paymentStatus()).isEqualTo("PAID");
        assertThat(settled.paidAmount()).isEqualByComparingTo("500.00");
        assertThat(settled.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("A party-less payment without an expense allocation is rejected")
    void partyLessPaymentRequiresExpenseAllocation() {
        assertThatThrownBy(() -> paymentService.create(new PaymentDto.CreatePaymentRequest(
                "CASH_OUT", null, new BigDecimal("100.00"), "MRU", LocalDate.now(), "CASH",
                null, null, null, null)))
                .isInstanceOf(BusinessException.class);
    }
}
