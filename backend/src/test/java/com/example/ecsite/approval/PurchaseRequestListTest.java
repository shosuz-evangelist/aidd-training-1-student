package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.TestDataHelper.Item;
import com.example.ecsite.approval.dto.PurchaseRequestListView;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.exception.ApprovalError;
import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.exception.EmployeeNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 8.1-8.4 - 承認一覧の 4 つの可否は、各操作と同じ判定から求める。 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestListTest {
    @Autowired PurchaseRequestService service;
    @Autowired DelegationService delegations;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    long staff;
    long sm;
    long otherSm;
    long dm;
    long purchasing;
    long delegateOfSm;
    long delegateOfPurchasing;
    long expiredDelegateOfSm;

    @BeforeEach
    void setUp() {
        data.reset();
        clock.set(OffsetDateTime.parse("2026-10-21T10:30:00+09:00"));
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        otherSm = emp("othersm", "総務部", EmployeeRole.SECTION_MANAGER);
        dm = emp("dm", "営業部", EmployeeRole.DEPARTMENT_MANAGER);
        purchasing = emp("pur", "購買部", EmployeeRole.PURCHASING);
        delegateOfSm = emp("delegate1", "総務部", EmployeeRole.STAFF);
        delegateOfPurchasing = emp("delegate2", "購買部", EmployeeRole.STAFF);
        expiredDelegateOfSm = emp("delegate3", "総務部", EmployeeRole.STAFF);
        delegations.register(sm, delegateOfSm, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
        delegations.register(purchasing, delegateOfPurchasing, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22));
        delegations.register(sm, expiredDelegateOfSm, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
    }

    private long emp(String name, String dept, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), dept, role);
    }

    private long request(RequestStatus status) {
        EmployeeRole pendingRole = status == RequestStatus.PENDING ? EmployeeRole.SECTION_MANAGER : null;
        return data.createRequest(staff, status, pendingRole, new Item(1_000, 1));
    }

    private PurchaseRequestListView item(long viewer, long id) {
        return service.list(viewer).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
    }

    private static List<Boolean> flags(PurchaseRequestListView v) {
        return List.of(v.canApprove(), v.canReturn(), v.canResubmit(), v.canOrder());
    }

    @Test
    void 承認待ちは課長本人と有効な代理人だけが承認と差し戻しできる() {
        long id = request(RequestStatus.PENDING);

        assertThat(flags(item(sm, id))).containsExactly(true, true, false, false);
        assertThat(flags(item(delegateOfSm, id))).containsExactly(true, true, false, false);
        for (long viewer : new long[] {otherSm, dm, staff, purchasing, delegateOfPurchasing, expiredDelegateOfSm}) {
            assertThat(flags(item(viewer, id))).as("viewer %d", viewer).containsExactly(false, false, false, false);
        }
    }

    @Test
    void 差し戻しの申請は申請者だけが出し直せる() {
        long id = request(RequestStatus.RETURNED);

        assertThat(flags(item(staff, id))).containsExactly(false, false, true, false);
        for (long viewer : new long[] {sm, otherSm, dm, purchasing, delegateOfSm}) {
            assertThat(flags(item(viewer, id))).as("viewer %d", viewer).containsExactly(false, false, false, false);
        }
    }

    @Test
    void 承認済みの申請は購買部だけが発注でき購買部の代理人はできない() {
        long id = request(RequestStatus.APPROVED);

        assertThat(flags(item(purchasing, id))).containsExactly(false, false, false, true);
        for (long viewer : new long[] {delegateOfPurchasing, staff, sm, dm, delegateOfSm}) {
            assertThat(flags(item(viewer, id))).as("viewer %d", viewer).containsExactly(false, false, false, false);
        }
    }

    @Test
    void 発注済みの申請はだれも操作できない() {
        long id = request(RequestStatus.ORDERED);

        for (long viewer : new long[] {staff, sm, otherSm, dm, purchasing, delegateOfSm, delegateOfPurchasing}) {
            assertThat(flags(item(viewer, id))).as("viewer %d", viewer).containsExactly(false, false, false, false);
        }
    }

    @Test
    void 一覧は閲覧者によらず全ての申請を表示項目だけで返す() {
        long pending = request(RequestStatus.PENDING);
        long returned = request(RequestStatus.RETURNED);
        long approved = request(RequestStatus.APPROVED);
        long ordered = request(RequestStatus.ORDERED);

        for (long viewer : new long[] {staff, sm, dm, purchasing, delegateOfSm}) {
            List<PurchaseRequestListView> list = service.list(viewer);
            assertThat(list).extracting(PurchaseRequestListView::id)
                    .containsExactlyInAnyOrder(pending, returned, approved, ordered);
        }
        PurchaseRequestListView p = item(sm, pending);
        assertThat(p.applicantId()).isEqualTo(staff);
        assertThat(p.totalAmount()).isEqualTo(1_000);
        assertThat(p.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(p.pendingRole()).isEqualTo(EmployeeRole.SECTION_MANAGER);
        assertThat(item(sm, approved).pendingRole()).isNull();
    }

    @Test
    void 閲覧者がいなければ例外() {
        request(RequestStatus.PENDING);
        assertThatThrownBy(() -> service.list(999_999L)).isInstanceOf(EmployeeNotFoundException.class);
    }

    private void assertSucceedsIff(boolean flag, Runnable operation) {
        if (flag) {
            operation.run();
        } else {
            assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApprovalException.class,
                    e -> assertThat(e.getError()).isIn(ApprovalError.A01, ApprovalError.A03));
        }
    }

    @Test
    void 可否と実際の操作の成否は一致する() {
        for (RequestStatus status : RequestStatus.values()) {
            for (long viewer : new long[] {staff, sm, otherSm, dm, purchasing, delegateOfSm, delegateOfPurchasing,
                    expiredDelegateOfSm}) {
                for (String op : new String[] {"approve", "return", "resubmit", "order"}) {
                    long id = request(status);
                    PurchaseRequestListView v = item(viewer, id);
                    boolean flag = switch (op) {
                        case "approve" -> v.canApprove();
                        case "return" -> v.canReturn();
                        case "resubmit" -> v.canResubmit();
                        default -> v.canOrder();
                    };
                    Runnable operation = switch (op) {
                        case "approve" -> () -> service.approve(id, viewer);
                        case "return" -> () -> service.returnRequest(id, viewer, "理由");
                        case "resubmit" -> () -> service.checkResubmit(id, viewer);
                        default -> () -> service.order(id, viewer);
                    };
                    assertSucceedsIff(flag, operation);
                }
            }
        }
    }

    @Test
    void 差し戻しの可否は理由の有無に左右されない() {
        long id = request(RequestStatus.PENDING);

        assertThat(item(sm, id).canReturn()).isTrue();
        assertThatThrownBy(() -> service.returnRequest(id, sm, " "))
                .isInstanceOfSatisfying(ApprovalException.class, e -> assertThat(e.getError()).isEqualTo(ApprovalError.A02));
        assertThat(item(sm, id).canReturn()).isTrue();
    }
}
