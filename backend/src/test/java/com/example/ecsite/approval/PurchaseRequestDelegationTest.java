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

/** 要件 7.2-7.6, 9.2, 9.3 - 代理人の承認と差し戻し。 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestDelegationTest {
    private static final String REASON = "見積書が添付されていません";
    private static final LocalDate START = LocalDate.of(2026, 10, 20);
    private static final LocalDate END = LocalDate.of(2026, 10, 22);

    @Autowired PurchaseRequestService service;
    @Autowired DelegationService delegations;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    long staff;
    long sm;
    long delegate;
    long otherSm;

    @BeforeEach
    void setUp() {
        data.reset();
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        delegate = emp("delegate", "営業部", EmployeeRole.STAFF);
        otherSm = emp("osm", "総務部", EmployeeRole.SECTION_MANAGER);
        delegations.register(sm, delegate, START, END);
    }

    private long emp(String name, String dept, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), dept, role);
    }

    private void at(String dateTime) {
        clock.set(OffsetDateTime.parse(dateTime + "+09:00"));
    }

    private long pending() {
        return data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
    }

    private long historyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM approval_history", Long.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT status, pending_role FROM purchase_request WHERE id = ?", id);
    }

    private void assertApproveRejected(long id, long actor, ApprovalError error) {
        Map<String, Object> before = row(id);
        long historyBefore = historyCount();
        assertThatThrownBy(() -> service.approve(id, actor))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.getError()).isEqualTo(error));
        assertThat(row(id)).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
    }

    private void assertReturnRejected(long id, long actor, ApprovalError error) {
        Map<String, Object> before = row(id);
        long historyBefore = historyCount();
        assertThatThrownBy(() -> service.returnRequest(id, actor, REASON))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.getError()).isEqualTo(error));
        assertThat(row(id)).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
    }

    @Test
    void 開始日の0時の代理人の承認は通り代理元が履歴に残る() {
        at("2026-10-20T00:00:00");
        long id = pending();

        PurchaseRequestView v = service.approve(id, delegate);

        assertThat(v.status()).isEqualTo(RequestStatus.APPROVED);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT actor_id, on_behalf_of_id, action FROM approval_history WHERE request_id = ?", id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("actor_id", delegate).containsEntry("on_behalf_of_id", sm)
                .containsEntry("action", "APPROVE");
    }

    @Test
    void 終了日の23時59分の代理人の承認は通る() {
        at("2026-10-22T23:59:00");
        long id = pending();

        assertThat(service.approve(id, delegate).status()).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    void 開始日の前日と終了日の翌日はA01で何も変わらない() {
        long id = pending();
        at("2026-10-19T23:59:00");
        assertApproveRejected(id, delegate, ApprovalError.A01);
        assertReturnRejected(id, delegate, ApprovalError.A01);
        at("2026-10-23T00:00:00");
        assertApproveRejected(id, delegate, ApprovalError.A01);
        assertReturnRejected(id, delegate, ApprovalError.A01);
    }

    @Test
    void 代理元が資格を満たさない別の部署の申請は代理期間中でもA01() {
        at("2026-10-21T10:00:00");
        long otherStaff = emp("ostaff", "総務部", EmployeeRole.STAFF);
        long id = data.createRequest(otherStaff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                new Item(1_000, 1));

        assertApproveRejected(id, delegate, ApprovalError.A01);
        assertReturnRejected(id, delegate, ApprovalError.A01);
    }

    @Test
    void 代理人でも代理元の役割が合わない段階の申請はA01() {
        at("2026-10-21T10:00:00");
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.DEPARTMENT_MANAGER,
                new Item(200_000, 1));

        assertApproveRejected(id, delegate, ApprovalError.A01);
    }

    @Test
    void 代理人の差し戻しは理由と代理元が履歴に残る() {
        at("2026-10-21T10:00:00");
        long id = pending();

        PurchaseRequestView v = service.returnRequest(id, delegate, REASON);

        assertThat(v.status()).isEqualTo(RequestStatus.RETURNED);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT actor_id, on_behalf_of_id, action, reason FROM approval_history WHERE request_id = ?", id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("actor_id", delegate).containsEntry("on_behalf_of_id", sm)
                .containsEntry("action", "RETURN").containsEntry("reason", REASON);
    }

    @Test
    void 代理人の差し戻しでも理由が空ならA02で何も変わらない() {
        at("2026-10-21T10:00:00");
        long id = pending();
        Map<String, Object> before = row(id);

        assertThatThrownBy(() -> service.returnRequest(id, delegate, "　"))
                .isInstanceOfSatisfying(ApprovalException.class,
                        e -> assertThat(e.getError()).isEqualTo(ApprovalError.A02));
        assertThat(row(id)).isEqualTo(before);
        assertThat(historyCount()).isZero();
    }

    @Test
    void 本人の操作は代理期間中でも代理元が空になる() {
        at("2026-10-21T10:00:00");
        long id = pending();

        service.approve(id, sm);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT actor_id, on_behalf_of_id FROM approval_history WHERE request_id = ?", id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("actor_id", sm).containsEntry("on_behalf_of_id", null);
    }

    @Test
    void 代理元が複数あるときは代理設定のIDが小さいほうを代理元にする() {
        at("2026-10-21T10:00:00");
        long exec1 = emp("exec1", null, EmployeeRole.EXECUTIVE);
        long exec2 = emp("exec2", null, EmployeeRole.EXECUTIVE);
        long delegate2 = emp("delegate2", "営業部", EmployeeRole.STAFF);
        delegations.register(exec1, delegate2, START, END);
        delegations.register(exec2, delegate2, START, END);
        long id = executivePending();

        service.approve(id, delegate2);

        assertThat(jdbc.queryForObject("SELECT on_behalf_of_id FROM approval_history WHERE request_id = ?",
                Long.class, id)).isEqualTo(exec1);
    }

    @Test
    void 代理元が資格を満たさない代理は飛ばして資格のある代理元を探す() {
        at("2026-10-21T10:00:00");
        long exec = emp("exec", null, EmployeeRole.EXECUTIVE);
        long delegate2 = emp("delegate2", "営業部", EmployeeRole.STAFF);
        delegations.register(sm, delegate2, START, END);
        delegations.register(exec, delegate2, START, END);
        long id = executivePending();

        service.approve(id, delegate2);

        assertThat(jdbc.queryForObject("SELECT on_behalf_of_id FROM approval_history WHERE request_id = ?",
                Long.class, id)).isEqualTo(exec);
    }

    private long executivePending() {
        return data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.EXECUTIVE, new Item(2_000_000, 1));
    }

    @Test
    void 資格のない社員の代理期間外の操作はA01() {
        at("2026-10-21T10:00:00");
        long id = pending();
        assertApproveRejected(id, otherSm, ApprovalError.A01);
    }
}
