package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.TestDataHelper.Item;
import com.example.ecsite.approval.dto.PurchaseRequestView;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.exception.ApprovalError;
import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.service.DelegationService;
import com.example.ecsite.approval.service.PurchaseRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 6.1-6.5, 7.7, 9.1, 9.5 - 購買部の発注と、発注済みの申請への各操作の拒否。 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestOrderTest {
    @Autowired PurchaseRequestService service;
    @Autowired DelegationService delegations;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    long staff;
    long sm;
    long dm;
    long exec;
    long purchasing;
    long delegate;

    @BeforeEach
    void setUp() {
        data.reset();
        clock.set(OffsetDateTime.parse("2026-10-21T10:30:00+09:00"));
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        dm = emp("dm", "営業部", EmployeeRole.DEPARTMENT_MANAGER);
        exec = emp("exec", null, EmployeeRole.EXECUTIVE);
        purchasing = emp("pur", "購買部", EmployeeRole.PURCHASING);
        delegate = emp("delegate", "営業部", EmployeeRole.STAFF);
    }

    private long emp(String name, String dept, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), dept, role);
    }

    private long request(RequestStatus status) {
        EmployeeRole pendingRole = status == RequestStatus.PENDING ? EmployeeRole.SECTION_MANAGER : null;
        return data.createRequest(staff, status, pendingRole, new Item(1_000, 1));
    }

    private long historyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM approval_history", Long.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT status, pending_role FROM purchase_request WHERE id = ?", id);
    }

    private void assertRejected(long id, ApprovalError error, Runnable operation) {
        Map<String, Object> before = row(id);
        long historyBefore = historyCount();
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.getError()).isEqualTo(error));
        assertThat(row(id)).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
    }

    private void assertOrderRejected(long id, long actor, ApprovalError error) {
        assertRejected(id, error, () -> service.order(id, actor));
    }

    @Test
    void 購買部が承認済みの申請を発注すると発注済みになり履歴が1件増える() {
        long id = request(RequestStatus.APPROVED);

        PurchaseRequestView view = service.order(id, purchasing);

        assertThat(view.status()).isEqualTo(RequestStatus.ORDERED);
        assertThat(view.pendingRole()).isNull();
        assertThat(row(id)).containsEntry("status", "ORDERED").containsEntry("pending_role", null);
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT actor_id, on_behalf_of_id, action, reason, acted_at FROM approval_history WHERE request_id = ?",
                id);
        assertThat(history).hasSize(1);
        Map<String, Object> h = history.get(0);
        assertThat(h.get("actor_id")).isEqualTo(purchasing);
        assertThat(h.get("on_behalf_of_id")).isNull();
        assertThat(h.get("action")).isEqualTo("ORDER");
        assertThat(h.get("reason")).isNull();
        assertThat(((java.sql.Timestamp) h.get("acted_at")).toInstant()).isEqualTo(clock.instant());
    }

    @Test
    void 購買部以外の発注はA01() {
        assertOrderRejected(request(RequestStatus.APPROVED), exec, ApprovalError.A01);
        assertOrderRejected(request(RequestStatus.APPROVED), dm, ApprovalError.A01);
        assertOrderRejected(request(RequestStatus.APPROVED), sm, ApprovalError.A01);
        assertOrderRejected(request(RequestStatus.APPROVED), staff, ApprovalError.A01);
    }

    @Test
    void 購買部の代理人は代理期間中でも発注できずA01() {
        delegations.register(purchasing, delegate, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
        assertOrderRejected(request(RequestStatus.APPROVED), delegate, ApprovalError.A01);
    }

    @Test
    void 承認待ちの発注はA03() {
        assertOrderRejected(request(RequestStatus.PENDING), purchasing, ApprovalError.A03);
    }

    @Test
    void 差し戻しの発注はA03() {
        assertOrderRejected(request(RequestStatus.RETURNED), purchasing, ApprovalError.A03);
    }

    @Test
    void 発注済みの再発注はA03で履歴が増えない() {
        long id = request(RequestStatus.APPROVED);
        service.order(id, purchasing);

        assertOrderRejected(id, purchasing, ApprovalError.A03);
        assertThat(historyCount()).isEqualTo(1);
    }

    @Test
    void 発注済みの申請への承認と差し戻しと出し直しと発注はすべてA03で状態も履歴も変わらない() {
        long id = request(RequestStatus.ORDERED);

        assertRejected(id, ApprovalError.A03, () -> service.approve(id, sm));
        assertRejected(id, ApprovalError.A03, () -> service.returnRequest(id, sm, "理由"));
        assertRejected(id, ApprovalError.A03, () -> service.checkResubmit(id, staff));
        assertOrderRejected(id, purchasing, ApprovalError.A03);
        assertThat(row(id)).containsEntry("status", "ORDERED");
    }

    @Test
    void 申請がなければ例外() {
        assertThatThrownBy(() -> service.order(999_999L, purchasing))
                .isInstanceOf(com.example.ecsite.approval.exception.RequestNotFoundException.class);
    }
}
