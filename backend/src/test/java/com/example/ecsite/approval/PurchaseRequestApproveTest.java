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
import com.example.ecsite.approval.exception.EmployeeNotFoundException;
import com.example.ecsite.approval.exception.RequestNotFoundException;
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

/** 要件 2.2-2.6, 2.8-2.11, 2.13, 2.14, 3.1-3.7, 9.1, 9.3, 9.5 - 承認。 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestApproveTest {
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

    private void assertRejected(long id, long actor, ApprovalError error) {
        Map<String, Object> before = row(id);
        long historyBefore = historyCount();
        assertThatThrownBy(() -> service.approve(id, actor))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.getError()).isEqualTo(error));
        assertThat(row(id)).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
    }

    @Test
    void 担当者の1000001円を課長部長役員の順に承認して承認済みになり履歴が3件() {
        PurchaseRequestView v = service.submit(staff, List.of(new ItemInput(1_000_001, 1)));

        PurchaseRequestView a = service.approve(v.id(), sm);
        assertThat(a.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(a.pendingRole()).isEqualTo(EmployeeRole.DEPARTMENT_MANAGER);
        assertThat(historyCount()).isEqualTo(1);

        PurchaseRequestView b = service.approve(v.id(), dm);
        assertThat(b.pendingRole()).isEqualTo(EmployeeRole.EXECUTIVE);
        assertThat(historyCount()).isEqualTo(2);

        PurchaseRequestView c = service.approve(v.id(), exec);
        assertThat(c.status()).isEqualTo(RequestStatus.APPROVED);
        assertThat(c.pendingRole()).isNull();
        assertThat(row(v.id())).containsEntry("status", "APPROVED").containsEntry("pending_role", null);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT actor_id, on_behalf_of_id, action, reason FROM approval_history WHERE request_id = ? ORDER BY id",
                v.id());
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("actor_id")).containsExactly(sm, dm, exec);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("on_behalf_of_id")).isNull();
            assertThat(r.get("action")).isEqualTo("APPROVE");
            assertThat(r.get("reason")).isNull();
        });
    }

    @Test
    void 履歴の日時はClockの時刻で日本時間の同じ瞬間() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
        service.approve(id, sm);

        OffsetDateTime actedAt = jdbc.queryForObject("SELECT acted_at FROM approval_history WHERE request_id = ?",
                OffsetDateTime.class, id);
        assertThat(actedAt.toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-20T10:30:00+09:00").toInstant());
    }

    @Test
    void 各帯の最後の承認で承認済みになる() {
        long band10k = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(10_000, 1));
        assertThat(service.approve(band10k, sm).status()).isEqualTo(RequestStatus.APPROVED);

        long band100k = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(100_000, 1));
        assertThat(service.approve(band100k, sm).status()).isEqualTo(RequestStatus.APPROVED);

        PurchaseRequestView v = service.submit(staff, List.of(new ItemInput(1_000_000, 1)));
        assertThat(service.approve(v.id(), sm).pendingRole()).isEqualTo(EmployeeRole.DEPARTMENT_MANAGER);
        assertThat(service.approve(v.id(), dm).status()).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    void 課長の申請の10000円以上は部長の承認で承認済みになる() {
        PurchaseRequestView v = service.submit(sm, List.of(new ItemInput(10_000, 1)));
        assertThat(v.pendingRole()).isEqualTo(EmployeeRole.DEPARTMENT_MANAGER);
        assertThat(service.approve(v.id(), dm).status()).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    void 別の部署の課長と部長はA01() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
        assertRejected(id, otherSm, ApprovalError.A01);

        long id2 = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.DEPARTMENT_MANAGER, new Item(200_000, 1));
        assertRejected(id2, otherDm, ApprovalError.A01);
    }

    @Test
    void 役割違いはA01() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
        assertRejected(id, dm, ApprovalError.A01);
        assertRejected(id, exec, ApprovalError.A01);
        assertRejected(id, staff, ApprovalError.A01);
        assertRejected(id, purchasing, ApprovalError.A01);
    }

    @Test
    void 承認待ちでない申請の承認はA03() {
        long returned = data.createRequest(staff, RequestStatus.RETURNED, null, new Item(1_000, 1));
        long approved = data.createRequest(staff, RequestStatus.APPROVED, null, new Item(1_000, 1));
        long ordered = data.createRequest(staff, RequestStatus.ORDERED, null, new Item(1_000, 1));
        assertRejected(returned, sm, ApprovalError.A03);
        assertRejected(approved, sm, ApprovalError.A03);
        assertRejected(ordered, sm, ApprovalError.A03);
    }

    @Test
    void 課長の10000円の自分の申請の承認はA01で9999円は承認できる() {
        long over = data.createRequest(sm, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(10_000, 1));
        assertRejected(over, sm, ApprovalError.A01);

        long dmOver = data.createRequest(dm, RequestStatus.PENDING, EmployeeRole.DEPARTMENT_MANAGER, new Item(10_000, 1));
        assertRejected(dmOver, dm, ApprovalError.A01);

        PurchaseRequestView own = service.submit(sm, List.of(new ItemInput(9_999, 1)));
        assertThat(service.approve(own.id(), sm).status()).isEqualTo(RequestStatus.APPROVED);
        assertThat(historyCount()).isEqualTo(1);
    }

    @Test
    void 存在しない申請は専用の例外で社員がいないときは社員の例外() {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
        assertThatThrownBy(() -> service.approve(999_999L, sm)).isInstanceOf(RequestNotFoundException.class);
        assertThatThrownBy(() -> service.approve(id, 999_999L)).isInstanceOf(EmployeeNotFoundException.class);
        assertThat(historyCount()).isZero();
        assertThat(row(id)).containsEntry("status", "PENDING");
    }
}
