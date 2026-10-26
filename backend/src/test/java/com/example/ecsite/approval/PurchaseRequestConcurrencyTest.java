package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.TestDataHelper.Item;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.exception.ApprovalError;
import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.service.DelegationService;
import com.example.ecsite.approval.service.PurchaseRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 要件 10.1, 10.2, 10.3, 10.5 - 同じ申請への同時の操作が 1 回分だけ反映される。
 * サービスの各メソッドを、ラッチで同時に放したスレッドから呼ぶ(各呼び出しは、サービスの @Transactional で別々のトランザクション)。
 * 負けた操作は、先に反映された状態を読み直したときは A01(承認待ちの役割が変わった)、
 * 同じ状態を読んで条件付き UPDATE が 0 行になったときは A03 になる。どちらも履歴を残さない。
 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestConcurrencyTest {
    private static final String REASON = "見積書が添付されていません";

    @Autowired PurchaseRequestService service;
    @Autowired DelegationService delegations;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    long staff;
    long sm;
    long delegate;
    long purchasing1;
    long purchasing2;

    @BeforeEach
    void setUp() {
        data.reset();
        clock.set(OffsetDateTime.parse("2026-10-21T10:00:00+09:00"));
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        delegate = emp("delegate", "営業部", EmployeeRole.STAFF);
        purchasing1 = emp("p1", "購買部", EmployeeRole.PURCHASING);
        purchasing2 = emp("p2", "購買部", EmployeeRole.PURCHASING);
        delegations.register(sm, delegate, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
    }

    private long emp(String name, String dept, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), dept, role);
    }

    /** 担当者の 1,000,001 円(課長、部長、役員の 3 段階)で、課長の承認待ち。 */
    private long pendingForSectionManager() {
        return data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000_001, 1));
    }

    /** 全ての操作を揃えてから同時に放し、各操作が投げた例外(成功なら null)を並びのとおりに返す。 */
    private List<Throwable> runConcurrently(List<Callable<Object>> operations) throws Exception {
        CountDownLatch ready = new CountDownLatch(operations.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(operations.size());
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Callable<Object> operation : operations) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        operation.call();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertRejectedAsConflict(Throwable t) {
        assertThat(t).isInstanceOfSatisfying(ApprovalException.class,
                e -> assertThat(e.getError()).isIn(ApprovalError.A01, ApprovalError.A03));
    }

    private String state(long id) {
        return jdbc.queryForObject("SELECT status || '/' || COALESCE(pending_role, '-') FROM purchase_request WHERE id = ?",
                String.class, id);
    }

    private List<String> actions(long id) {
        return jdbc.queryForList("SELECT action FROM approval_history WHERE request_id = ? ORDER BY id", String.class, id);
    }

    private long count(List<Throwable> results, boolean success) {
        return results.stream().filter(t -> (t == null) == success).count();
    }

    @RepeatedTest(10)
    void 要件10_1_承認者本人と代理人が同時に承認しても状態は1段階だけ進み履歴は1件() throws Exception {
        long id = pendingForSectionManager();
        List<Callable<Object>> operations = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            operations.add(() -> service.approve(id, sm));
            operations.add(() -> service.approve(id, delegate));
        }

        List<Throwable> results = runConcurrently(operations);

        assertThat(count(results, true)).isEqualTo(1);
        results.stream().filter(t -> t != null).forEach(PurchaseRequestConcurrencyTest::assertRejectedAsConflict);
        assertThat(state(id)).isEqualTo("PENDING/DEPARTMENT_MANAGER");
        assertThat(actions(id)).containsExactly("APPROVE");
    }

    @RepeatedTest(10)
    void 要件10_2_承認と差し戻しが同時なら先の1回分だけ反映され後の操作は履歴を増やさない() throws Exception {
        long id = pendingForSectionManager();
        List<Callable<Object>> operations = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            operations.add(() -> service.approve(id, sm));
            operations.add(() -> service.approve(id, delegate));
            operations.add(() -> service.returnRequest(id, sm, REASON));
            operations.add(() -> service.returnRequest(id, delegate, REASON));
        }

        List<Throwable> results = runConcurrently(operations);

        assertThat(count(results, true)).isEqualTo(1);
        results.stream().filter(t -> t != null).forEach(PurchaseRequestConcurrencyTest::assertRejectedAsConflict);
        String state = state(id);
        List<String> actions = actions(id);
        if (state.equals("PENDING/DEPARTMENT_MANAGER")) {
            assertThat(actions).containsExactly("APPROVE");
        } else {
            assertThat(state).isEqualTo("RETURNED/-");
            assertThat(actions).containsExactly("RETURN");
        }
    }

    @RepeatedTest(10)
    void 要件10_3_購買部の2人が同時に発注しても発注済みで履歴は1件() throws Exception {
        long id = data.createRequest(staff, RequestStatus.APPROVED, null, new Item(1_000, 1));
        List<Callable<Object>> operations = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            operations.add(() -> service.order(id, purchasing1));
            operations.add(() -> service.order(id, purchasing2));
        }

        List<Throwable> results = runConcurrently(operations);

        assertThat(count(results, true)).isEqualTo(1);
        results.stream().filter(t -> t != null).forEach(t -> assertThat(t).isInstanceOfSatisfying(ApprovalException.class,
                e -> assertThat(e.getError()).isEqualTo(ApprovalError.A03)));
        assertThat(state(id)).isEqualTo("ORDERED/-");
        assertThat(actions(id)).containsExactly("ORDER");
    }
}
