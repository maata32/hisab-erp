package com.minierp.phase1b;

import com.minierp.MiniErpApplication;
import com.minierp.sales.api.NumberingOperations;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory spec test: 100 concurrent threads each call nextDeliveryNumber().
 * Every returned number must be unique — proves REQUIRES_NEW + PESSIMISTIC_WRITE prevents gaps/duplicates.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Atomic document numbering — 100 concurrent threads, zero duplicates")
class AtomicNumberingIT {

    @Autowired
    NumberingOperations numbering;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

    UUID tenantId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Numbering Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "numtest-" + tenantId);
    }

    @Test
    void hundredConcurrentCallsProduceUniqueNumbers() throws InterruptedException {
        int threadCount = 100;
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    TenantContext.set(tenantId);
                    ready.countDown();
                    start.await();
                    String number = numbering.nextDeliveryNumber();
                    numbers.add(number);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(numbers).hasSize(threadCount);
        assertThat(numbers).allMatch(n -> n.startsWith("BL-"));
    }
}
