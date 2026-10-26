package com.example.ecsite.approval;

import com.example.ecsite.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V5 のマイグレーションが作る購買承認のテーブルと制約を、データベースに対して確かめる。 */
@SpringBootTest
class ApprovalSchemaTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TestDataHelper data;

    @BeforeEach
    void setUp() {
        data.reset();
    }

    @Test
    void 購買承認の5つのテーブルがある() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()",
                String.class);
        assertThat(tables).contains(
                "employee", "purchase_request", "purchase_request_item", "approval_history", "delegation");
    }

    @Test
    void 同じ部署に課長を2名入れるとデータベースが拒否する() {
        long first = data.createCustomer("kacho1@example.com");
        long second = data.createCustomer("kacho2@example.com");
        insertEmployee(first, "営業", "SECTION_MANAGER");

        assertThatThrownBy(() -> insertEmployee(second, "営業", "SECTION_MANAGER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 同じ部署に部長を2名入れるとデータベースが拒否する() {
        long first = data.createCustomer("bucho1@example.com");
        long second = data.createCustomer("bucho2@example.com");
        insertEmployee(first, "営業", "DEPARTMENT_MANAGER");

        assertThatThrownBy(() -> insertEmployee(second, "営業", "DEPARTMENT_MANAGER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 別の部署の課長と同じ部署の課長と部長は入れられる() {
        long a = data.createCustomer("a@example.com");
        long b = data.createCustomer("b@example.com");
        long c = data.createCustomer("c@example.com");
        insertEmployee(a, "営業", "SECTION_MANAGER");
        insertEmployee(b, "総務", "SECTION_MANAGER");
        insertEmployee(c, "営業", "DEPARTMENT_MANAGER");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee", Integer.class)).isEqualTo(3);
    }

    @Test
    void 担当者は同じ部署に何名でも入れられる() {
        long a = data.createCustomer("s1@example.com");
        long b = data.createCustomer("s2@example.com");
        insertEmployee(a, "営業", "STAFF");
        insertEmployee(b, "営業", "STAFF");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee", Integer.class)).isEqualTo(2);
    }

    @Test
    void 役員だけ部署がなく_役員以外は部署が必要() {
        long exec = data.createCustomer("exec@example.com");
        long staff = data.createCustomer("staff@example.com");
        long staff2 = data.createCustomer("staff2@example.com");

        jdbc.update("INSERT INTO employee (customer_id, department, role) VALUES (?, NULL, 'EXECUTIVE')", exec);
        assertThatThrownBy(() -> insertEmployee(staff, null, "STAFF"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEmployee(staff2, "営業", "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 明細の数量0と単価マイナスをデータベースが拒否する() {
        long applicant = data.createCustomer("applicant@example.com");
        insertEmployee(applicant, "営業", "STAFF");
        long request = insertRequest(applicant, 0, "RETURNED", null);

        assertThatThrownBy(() -> insertItem(request, 100, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertItem(request, -1, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertItem(request, 0, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_request_item", Integer.class)).isEqualTo(1);
    }

    @Test
    void 申請の状態と承認待ちの役割の対応をデータベースが保つ() {
        long applicant = data.createCustomer("applicant@example.com");
        insertEmployee(applicant, "営業", "STAFF");

        assertThatThrownBy(() -> insertRequest(applicant, 100, "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRequest(applicant, 100, "APPROVED", "SECTION_MANAGER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRequest(applicant, 100, "UNKNOWN", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertRequest(applicant, 100, "PENDING", "SECTION_MANAGER");
    }

    @Test
    void 履歴と代理の外部キーと操作の値を確かめる() {
        long applicant = data.createCustomer("applicant@example.com");
        insertEmployee(applicant, "営業", "STAFF");
        long request = insertRequest(applicant, 100, "RETURNED", null);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO approval_history (request_id, actor_id, action, acted_at) VALUES (?, ?, 'UNKNOWN', now())",
                request, applicant)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO approval_history (request_id, actor_id, action, acted_at) VALUES (?, ?, 'APPROVE', now())",
                request, applicant + 999)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "INSERT INTO approval_history (request_id, actor_id, action, acted_at) VALUES (?, ?, 'RETURN', now())",
                request, applicant);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO delegation (delegator_id, delegate_id, start_date, end_date) VALUES (?, ?, DATE '2026-10-01', DATE '2026-10-31')",
                applicant, applicant + 999)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "INSERT INTO delegation (delegator_id, delegate_id, start_date, end_date) VALUES (?, ?, DATE '2026-10-31', DATE '2026-10-01')",
                applicant, applicant);
    }

    private void insertEmployee(long customerId, String department, String role) {
        jdbc.update("INSERT INTO employee (customer_id, department, role) VALUES (?, ?, ?)",
                customerId, department, role);
    }

    private long insertRequest(long applicantId, long total, String status, String pendingRole) {
        return jdbc.queryForObject(
                "INSERT INTO purchase_request (applicant_id, total_amount, status, pending_role) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, applicantId, total, status, pendingRole);
    }

    private void insertItem(long requestId, long unitPrice, int quantity) {
        jdbc.update("INSERT INTO purchase_request_item (request_id, unit_price, quantity) VALUES (?, ?, ?)",
                requestId, unitPrice, quantity);
    }
}
