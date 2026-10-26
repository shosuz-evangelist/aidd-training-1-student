package com.example.ecsite.approval;

import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 10.1, 10.2, 10.3, 10.4 - 条件付き UPDATE で申請の状態を 1 回だけ進める。 */
@SpringBootTest
class PurchaseRequestConditionalUpdateTest {
    static final int THREADS = 12;

    @Autowired PurchaseRequestRepository repository;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    long applicant;

    @BeforeEach
    void setUp() {
        data.reset();
        applicant = data.createEmployee(data.createCustomer("a@example.com"), "営業部", EmployeeRole.STAFF);
    }

    @RepeatedTest(10)
    void 要件10_1_同じ条件で同時に呼んでも更新行数1を得るのは1つだけ() throws Exception {
        long id = data.createRequest(applicant, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                new TestDataHelper.Item(1000, 3));

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return advance(id, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                        RequestStatus.PENDING, EmployeeRole.DEPARTMENT_MANAGER);
            }));
        }
        ready.await();
        start.countDown();

        int won = 0;
        int lost = 0;
        for (Future<Integer> r : results) {
            int rows = r.get();
            if (rows == 1) won++;
            else if (rows == 0) lost++;
        }
        pool.shutdown();

        assertThat(won).isEqualTo(1);
        assertThat(lost).isEqualTo(THREADS - 1);
        assertThat(stateOf(id)).isEqualTo("PENDING/DEPARTMENT_MANAGER");
    }

    @Test
    void 要件10_2_承認待ちの役割がNULLの申請でも一致の判定が働く() {
        long id = data.createRequest(applicant, RequestStatus.APPROVED, null, new TestDataHelper.Item(500, 2));

        assertThat(advance(id, RequestStatus.APPROVED, null, RequestStatus.ORDERED, null)).isEqualTo(1);
        assertThat(stateOf(id)).isEqualTo("ORDERED/null");
        assertThat(advance(id, RequestStatus.APPROVED, null, RequestStatus.ORDERED, null)).isEqualTo(0);
    }

    @Test
    void 要件10_3_NULLの申請を同時に進めても1つだけが成功する() throws Exception {
        long id = data.createRequest(applicant, RequestStatus.APPROVED, null, new TestDataHelper.Item(500, 2));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return advance(id, RequestStatus.APPROVED, null, RequestStatus.ORDERED, null);
            }));
        }
        start.countDown();
        int sum = 0;
        for (Future<Integer> r : results) sum += r.get();
        pool.shutdown();

        assertThat(sum).isEqualTo(1);
        assertThat(stateOf(id)).isEqualTo("ORDERED/null");
    }

    @Test
    void 要件10_4_読んだ時点の状態か役割が違えば0を返して何も変えない() {
        long pending = data.createRequest(applicant, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                new TestDataHelper.Item(1000, 1));
        long approved = data.createRequest(applicant, RequestStatus.APPROVED, null, new TestDataHelper.Item(1000, 1));

        // 役割が違う
        assertThat(advance(pending, RequestStatus.PENDING, EmployeeRole.EXECUTIVE,
                RequestStatus.APPROVED, null)).isEqualTo(0);
        // 状態が違う
        assertThat(advance(pending, RequestStatus.RETURNED, EmployeeRole.SECTION_MANAGER,
                RequestStatus.APPROVED, null)).isEqualTo(0);
        // NULL の申請に役割を持つ条件
        assertThat(advance(approved, RequestStatus.APPROVED, EmployeeRole.EXECUTIVE,
                RequestStatus.ORDERED, null)).isEqualTo(0);
        // 承認待ちの申請に NULL の条件
        assertThat(advance(pending, RequestStatus.PENDING, null,
                RequestStatus.APPROVED, null)).isEqualTo(0);
        // 存在しない ID
        assertThat(advance(999_999L, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                RequestStatus.APPROVED, null)).isEqualTo(0);

        assertThat(stateOf(pending)).isEqualTo("PENDING/SECTION_MANAGER");
        assertThat(stateOf(approved)).isEqualTo("APPROVED/null");
    }

    /** 実際の呼び出しと同じく、操作ごとに 1 つのトランザクションの中で実行する。 */
    private int advance(Long id, RequestStatus fromStatus, EmployeeRole fromRole,
                        RequestStatus toStatus, EmployeeRole toRole) {
        return tx.execute(status -> repository.advance(id, fromStatus, fromRole, toStatus, toRole));
    }

    private String stateOf(long id) {
        return jdbc.queryForObject("SELECT status || '/' || COALESCE(pending_role, 'null') FROM purchase_request WHERE id = ?",
                String.class, id);
    }
}
