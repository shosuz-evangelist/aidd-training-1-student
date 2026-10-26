package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.TestDataHelper.Item;
import com.example.ecsite.approval.dto.ItemInput;
import com.example.ecsite.approval.dto.PurchaseRequestView;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.exception.ApprovalError;
import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.service.PurchaseRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 4.1-4.6, 9.1, 9.4, 9.5 - 理由をつけた差し戻し。 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestReturnTest {
    private static final String REASON = "見積書が添付されていません";

    @Autowired PurchaseRequestService service;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    long staff;
    long sm;
    long dm;
    long exec;
    long otherSm;
    long otherDm;
    long purchasing;

    @BeforeEach
    void setUp() {
        data.reset();
        clock.set(OffsetDateTime.parse("2026-10-20T10:30:00+09:00"));
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        dm = emp("dm", "営業部", EmployeeRole.DEPARTMENT_MANAGER);
        exec = emp("exec", null, EmployeeRole.EXECUTIVE);
        otherSm = emp("osm", "総務部", EmployeeRole.SECTION_MANAGER);
        otherDm = emp("odm", "総務部", EmployeeRole.DEPARTMENT_MANAGER);
        purchasing = emp("pur", "購買部", EmployeeRole.PURCHASING);
    }

    private long emp(String name, String dept, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), dept, role);
    }

    private long historyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM approval_history", Long.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT status, pending_role FROM purchase_request WHERE id = ?", id);
    }

    private void assertRejected(long id, long actor, String reason, ApprovalError error) {
        Map<String, Object> before = row(id);
        long historyBefore = historyCount();
        assertThatThrownBy(() -> service.returnRequest(id, actor, reason))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.getError()).isEqualTo(error));
        assertThat(row(id)).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
    }

    @Test
    void 理由ありの差し戻しは差し戻しになり理由と操作者と日時が履歴に残る() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));

        PurchaseRequestView v = service.returnRequest(id, sm, REASON);

        assertThat(v.status()).isEqualTo(RequestStatus.RETURNED);
        assertThat(v.pendingRole()).isNull();
        assertThat(row(id)).containsEntry("status", "RETURNED").containsEntry("pending_role", null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT actor_id, on_behalf_of_id, action, reason, acted_at FROM approval_history WHERE request_id = ?",
                id);
        assertThat(rows).hasSize(1);
        Map<String, Object> h = rows.get(0);
        assertThat(h.get("actor_id")).isEqualTo(sm);
        assertThat(h.get("on_behalf_of_id")).isNull();
        assertThat(h.get("action")).isEqualTo("RETURN");
        assertThat(h.get("reason")).isEqualTo(REASON);
        assertThat(((java.sql.Timestamp) h.get("acted_at")).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-10-20T10:30:00+09:00").toInstant());
    }

    @Test
    void 理由なしと空白だけの理由はA02で状態も履歴も変わらない() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));

        assertRejected(id, sm, null, ApprovalError.A02);
        assertRejected(id, sm, "", ApprovalError.A02);
        assertRejected(id, sm, "   ", ApprovalError.A02);
        assertRejected(id, sm, "　　", ApprovalError.A02);
        assertRejected(id, sm, " 　 ", ApprovalError.A02);
        assertThat(row(id)).containsEntry("status", "PENDING");
    }

    @Test
    void 二段階目の承認者の差し戻しで先の承認の履歴が残る() {
        PurchaseRequestView v = service.submit(staff, List.of(new ItemInput(1_000_001, 1)));
        service.approve(v.id(), sm);

        PurchaseRequestView returned = service.returnRequest(v.id(), dm, REASON);

        assertThat(returned.status()).isEqualTo(RequestStatus.RETURNED);
        assertThat(returned.pendingRole()).isNull();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT actor_id, action, reason FROM approval_history WHERE request_id = ? ORDER BY id", v.id());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("actor_id", sm).containsEntry("action", "APPROVE")
                .containsEntry("reason", null);
        assertThat(rows.get(1)).containsEntry("actor_id", dm).containsEntry("action", "RETURN")
                .containsEntry("reason", REASON);
    }

    @Test
    void 別の部署の課長と部長はA01() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
        assertRejected(id, otherSm, REASON, ApprovalError.A01);

        long id2 = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.DEPARTMENT_MANAGER, new Item(200_000, 1));
        assertRejected(id2, otherDm, REASON, ApprovalError.A01);
    }

    @Test
    void 役割違いはA01() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
        assertRejected(id, dm, REASON, ApprovalError.A01);
        assertRejected(id, exec, REASON, ApprovalError.A01);
        assertRejected(id, staff, REASON, ApprovalError.A01);
        assertRejected(id, purchasing, REASON, ApprovalError.A01);
    }

    @Test
    void 承認待ちでない申請の差し戻しはA03() {
        long returned = data.createRequest(staff, RequestStatus.RETURNED, null, new Item(1_000, 1));
        long approved = data.createRequest(staff, RequestStatus.APPROVED, null, new Item(1_000, 1));
        long ordered = data.createRequest(staff, RequestStatus.ORDERED, null, new Item(1_000, 1));
        assertRejected(returned, sm, REASON, ApprovalError.A03);
        assertRejected(approved, sm, REASON, ApprovalError.A03);
        assertRejected(ordered, sm, REASON, ApprovalError.A03);
    }
}
