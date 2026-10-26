package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.TestDataHelper.Item;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.exception.ApprovalError;
import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.exception.RequestNotFoundException;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 5.2, 5.3, 7.7 - 出し直しの権限と状態の確認。承認待ちへの遷移の書き込みは含まない。 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestResubmitCheckTest {
    @Autowired PurchaseRequestService service;
    @Autowired DelegationService delegations;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    long staff;
    long sm;
    long delegate;
    long otherStaff;

    @BeforeEach
    void setUp() {
        data.reset();
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        delegate = emp("delegate", "営業部", EmployeeRole.STAFF);
        otherStaff = emp("other", "営業部", EmployeeRole.STAFF);
        clock.set(OffsetDateTime.parse("2026-10-21T10:00:00+09:00"));
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

    private void assertRejected(long id, long actor, ApprovalError error) {
        Map<String, Object> before = row(id);
        long historyBefore = historyCount();
        assertThatThrownBy(() -> service.checkResubmit(id, actor))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.getError()).isEqualTo(error));
        assertThat(row(id)).isEqualTo(before);
        assertThat(historyCount()).isEqualTo(historyBefore);
    }

    @Test
    void 申請者本人の差し戻しの申請は確認に通り状態も履歴も変わらない() {
        long id = request(RequestStatus.RETURNED);
        Map<String, Object> before = row(id);

        assertThatCode(() -> service.checkResubmit(id, staff)).doesNotThrowAnyException();

        assertThat(row(id)).isEqualTo(before).containsEntry("status", "RETURNED");
        assertThat(historyCount()).isZero();
    }

    @Test
    void 申請者以外の出し直しはA01() {
        assertRejected(request(RequestStatus.RETURNED), otherStaff, ApprovalError.A01);
    }

    @Test
    void 承認者でも申請者でなければA01() {
        assertRejected(request(RequestStatus.RETURNED), sm, ApprovalError.A01);
    }

    @Test
    void 承認待ちの出し直しはA03() {
        assertRejected(request(RequestStatus.PENDING), staff, ApprovalError.A03);
    }

    @Test
    void 承認済みの出し直しはA03() {
        assertRejected(request(RequestStatus.APPROVED), staff, ApprovalError.A03);
    }

    @Test
    void 発注済みの出し直しはA03() {
        assertRejected(request(RequestStatus.ORDERED), staff, ApprovalError.A03);
    }

    @Test
    void 承認者の代理人は代理期間中でも出し直しできずA01() {
        delegations.register(staff, delegate, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
        delegations.register(sm, delegate, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
        assertRejected(request(RequestStatus.RETURNED), delegate, ApprovalError.A01);
    }

    @Test
    void 申請がなければRequestNotFoundException() {
        assertThatThrownBy(() -> service.checkResubmit(999_999L, staff))
                .isInstanceOf(RequestNotFoundException.class);
    }
}
