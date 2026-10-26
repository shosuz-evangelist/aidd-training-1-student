package com.example.ecsite.approval;

import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 承認のテスト補助(reset・社員と申請の作成)と、テストのデータベースにサンプルが入らないことを確かめる。 */
@SpringBootTest
class ApprovalTestSupportTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TestDataHelper data;
    @Autowired PurchaseRequestRepository requests;

    @BeforeEach
    void setUp() {
        data.reset();
    }

    @Test
    void resetの後に社員を作って申請を保存できる() {
        long customerId = data.createCustomer("staff@example.com");
        long applicantId = data.createEmployee(customerId, "営業部", EmployeeRole.STAFF);

        long requestId = data.createRequest(applicantId, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                new TestDataHelper.Item(1_000, 3), new TestDataHelper.Item(500, 2));

        assertThat(applicantId).isEqualTo(customerId);
        assertThat(jdbc.queryForObject("SELECT role FROM employee WHERE customer_id = ?", String.class, applicantId))
                .isEqualTo("STAFF");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM purchase_request WHERE id = ?", Long.class, requestId))
                .isEqualTo(4_000L);
        assertThat(jdbc.queryForObject("SELECT pending_role FROM purchase_request WHERE id = ?", String.class, requestId))
                .isEqualTo("SECTION_MANAGER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_request_item WHERE request_id = ?",
                Integer.class, requestId)).isEqualTo(2);
        assertThat(requests.findById(requestId)).isPresent();
    }

    @Test
    void 差し戻しなど承認待ちでない申請は承認待ちの役割なしで作れる() {
        long applicantId = data.createEmployee(data.createCustomer("s2@example.com"), "営業部", EmployeeRole.STAFF);

        long requestId = data.createRequest(applicantId, RequestStatus.APPROVED, null,
                new TestDataHelper.Item(100, 1));

        assertThat(jdbc.queryForObject("SELECT status FROM purchase_request WHERE id = ?", String.class, requestId))
                .isEqualTo("APPROVED");
    }

    @Test
    void resetは5つの新しいテーブルを空にする() {
        long applicantId = data.createEmployee(data.createCustomer("s3@example.com"), "営業部", EmployeeRole.STAFF);
        long requestId = data.createRequest(applicantId, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                new TestDataHelper.Item(100, 1));
        jdbc.update("INSERT INTO approval_history (request_id, actor_id, action, acted_at) VALUES (?, ?, 'APPROVE', now())",
                requestId, applicantId);
        jdbc.update("INSERT INTO delegation (delegator_id, delegate_id, start_date, end_date) VALUES (?, ?, current_date, current_date)",
                applicantId, applicantId);

        data.reset();

        for (String table : new String[]{"employee", "purchase_request", "purchase_request_item",
                "approval_history", "delegation"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                    .as(table).isZero();
        }
    }

    @Test
    void テストのデータベースにはサンプルの社員が入っていない() {
        // reset が空にする前に、Flyway がサンプルを適用していないことを履歴で確かめる
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE script LIKE 'R__sample%'", Integer.class)).isZero();
    }
}
