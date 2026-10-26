package com.example.ecsite.approval.service;

import com.example.ecsite.approval.dto.ItemInput;
import com.example.ecsite.approval.entity.Employee;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.PurchaseRequest;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.ecsite.approval.entity.EmployeeRole.DEPARTMENT_MANAGER;
import static com.example.ecsite.approval.entity.EmployeeRole.EXECUTIVE;
import static com.example.ecsite.approval.entity.EmployeeRole.PURCHASING;
import static com.example.ecsite.approval.entity.EmployeeRole.SECTION_MANAGER;
import static com.example.ecsite.approval.entity.EmployeeRole.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 1.1, 1.2, 1.3, 2.1-2.14, 3.3, 3.4, 3.5, 4.4 - DB を使わない純粋な単体テスト。 */
class ApprovalPolicyTest {
    private final ApprovalPolicy policy = new ApprovalPolicy();

    private void assertRoute(EmployeeRole applicant, long amount, EmployeeRole... expected) {
        assertThat(policy.route(applicant, amount))
                .as("%s %d 円", applicant, amount)
                .containsExactly(expected);
    }

    @Test
    void 担当者の並びは決裁権限表のとおり() {
        assertRoute(STAFF, 9_999, SECTION_MANAGER);                                   // 2.1
        assertRoute(STAFF, 10_000, SECTION_MANAGER);                                  // 2.2
        assertRoute(STAFF, 100_000, SECTION_MANAGER);                                 // 2.3
        assertRoute(STAFF, 100_001, SECTION_MANAGER, DEPARTMENT_MANAGER);             // 2.4
        assertRoute(STAFF, 1_000_000, SECTION_MANAGER, DEPARTMENT_MANAGER);           // 2.5
        assertRoute(STAFF, 1_000_001, SECTION_MANAGER, DEPARTMENT_MANAGER, EXECUTIVE); // 2.6
    }

    @Test
    void 課長の並びは決裁権限表のとおり() {
        assertRoute(SECTION_MANAGER, 9_999, SECTION_MANAGER);                         // 2.7
        assertRoute(SECTION_MANAGER, 10_000, DEPARTMENT_MANAGER);                     // 2.8
        assertRoute(SECTION_MANAGER, 100_000, DEPARTMENT_MANAGER);                    // 2.9
        assertRoute(SECTION_MANAGER, 100_001, DEPARTMENT_MANAGER);                    // 2.9
        assertRoute(SECTION_MANAGER, 1_000_000, DEPARTMENT_MANAGER);                  // 2.10
        assertRoute(SECTION_MANAGER, 1_000_001, DEPARTMENT_MANAGER, EXECUTIVE);       // 2.11
    }

    @Test
    void 部長の並びは決裁権限表のとおり() {
        assertRoute(DEPARTMENT_MANAGER, 9_999, DEPARTMENT_MANAGER);                   // 2.12
        assertRoute(DEPARTMENT_MANAGER, 10_000, EXECUTIVE);                           // 2.13
        assertRoute(DEPARTMENT_MANAGER, 100_000, EXECUTIVE);
        assertRoute(DEPARTMENT_MANAGER, 100_001, EXECUTIVE);
        assertRoute(DEPARTMENT_MANAGER, 1_000_000, EXECUTIVE);
        assertRoute(DEPARTMENT_MANAGER, 1_000_001, EXECUTIVE);
    }

    @Test
    void 申請者が担当者_課長_部長以外なら例外() {
        assertThatThrownBy(() -> policy.route(EXECUTIVE, 1_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.route(PURCHASING, 1_000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 次の承認者は並びの次の役割で_最後なら空() {
        // 担当者 1,000,001 円: 課長 -> 部長 -> 役員
        assertThat(policy.next(STAFF, 1_000_001, SECTION_MANAGER)).contains(DEPARTMENT_MANAGER);
        assertThat(policy.next(STAFF, 1_000_001, DEPARTMENT_MANAGER)).contains(EXECUTIVE);
        assertThat(policy.next(STAFF, 1_000_001, EXECUTIVE)).isEmpty();
        // 担当者 10,000 円: 課長だけ
        assertThat(policy.next(STAFF, 10_000, SECTION_MANAGER)).isEmpty();
        // 担当者 100,001 円: 課長 -> 部長
        assertThat(policy.next(STAFF, 100_001, SECTION_MANAGER)).contains(DEPARTMENT_MANAGER);
        assertThat(policy.next(STAFF, 100_001, DEPARTMENT_MANAGER)).isEmpty();
        // 課長 9,999 円: 本人だけ
        assertThat(policy.next(SECTION_MANAGER, 9_999, SECTION_MANAGER)).isEmpty();
        // 課長 1,000,001 円: 部長 -> 役員
        assertThat(policy.next(SECTION_MANAGER, 1_000_001, DEPARTMENT_MANAGER)).contains(EXECUTIVE);
        assertThat(policy.next(SECTION_MANAGER, 1_000_001, EXECUTIVE)).isEmpty();
        // 部長 10,000 円: 役員だけ
        assertThat(policy.next(DEPARTMENT_MANAGER, 10_000, EXECUTIVE)).isEmpty();
        assertThat(policy.next(DEPARTMENT_MANAGER, 9_999, DEPARTMENT_MANAGER)).isEmpty();
    }

    @Test
    void 並びにない役割の次は空() {
        Optional<EmployeeRole> next = policy.next(STAFF, 10_000, EXECUTIVE);
        assertThat(next).isEmpty();
    }

    @Test
    void 明細の合計は単価かける数量の和() {
        assertThat(policy.totalAmount(List.of(new ItemInput(1_000, 3)))).isEqualTo(3_000);                       // 1.1
        assertThat(policy.totalAmount(List.of(new ItemInput(500, 2), new ItemInput(2_000, 1)))).isEqualTo(3_000); // 1.2
        assertThat(policy.totalAmount(List.of(new ItemInput(0, 1)))).isZero();                                    // 1.3
    }

    @Test
    void 単価かける数量の桁あふれは例外() {
        assertThatThrownBy(() -> policy.totalAmount(List.of(new ItemInput(Long.MAX_VALUE, 2))))
                .isInstanceOf(InvalidApprovalRequestException.class);
    }

    @Test
    void 合計の桁あふれは例外() {
        assertThatThrownBy(() -> policy.totalAmount(
                List.of(new ItemInput(Long.MAX_VALUE, 1), new ItemInput(1, 1))))
                .isInstanceOf(InvalidApprovalRequestException.class);
    }

    private static Employee employee(long id, String department, EmployeeRole role) {
        return new Employee(id, department, role);
    }

    private static PurchaseRequest pending(long applicantId, long amount, EmployeeRole pendingRole) {
        return new PurchaseRequest(applicantId, amount, RequestStatus.PENDING, pendingRole, OffsetDateTime.now());
    }

    @Test
    void 部署が同じ課長と部長は承認できる() {
        Employee applicant = employee(1, "営業", STAFF);
        assertThat(policy.isEligibleApprover(pending(1, 50_000, SECTION_MANAGER), applicant,
                employee(2, "営業", SECTION_MANAGER))).isTrue();
        assertThat(policy.isEligibleApprover(pending(1, 500_000, DEPARTMENT_MANAGER), applicant,
                employee(3, "営業", DEPARTMENT_MANAGER))).isTrue();
    }

    @Test
    void 部署違いの課長と部長は承認できない() { // 3.4
        Employee applicant = employee(1, "営業", STAFF);
        assertThat(policy.isEligibleApprover(pending(1, 50_000, SECTION_MANAGER), applicant,
                employee(5, "総務", SECTION_MANAGER))).isFalse();
        assertThat(policy.isEligibleApprover(pending(1, 500_000, DEPARTMENT_MANAGER), applicant,
                employee(6, "総務", DEPARTMENT_MANAGER))).isFalse();
    }

    @Test
    void 役員は部署が違っても承認できる() { // 3.3
        Employee applicant = employee(1, "営業", STAFF);
        assertThat(policy.isEligibleApprover(pending(1, 2_000_000, EXECUTIVE), applicant,
                employee(7, null, EXECUTIVE))).isTrue();
        assertThat(policy.isEligibleApprover(pending(1, 2_000_000, EXECUTIVE), applicant,
                employee(8, "総務", EXECUTIVE))).isTrue();
    }

    @Test
    void 承認待ちの役割と違う役割は承認できない() { // 3.5, 4.4
        Employee applicant = employee(1, "営業", STAFF);
        assertThat(policy.isEligibleApprover(pending(1, 500_000, DEPARTMENT_MANAGER), applicant,
                employee(2, "営業", SECTION_MANAGER))).isFalse();
        assertThat(policy.isEligibleApprover(pending(1, 500_000, DEPARTMENT_MANAGER), applicant,
                employee(7, null, EXECUTIVE))).isFalse();
        assertThat(policy.isEligibleApprover(pending(1, 50_000, SECTION_MANAGER), applicant,
                employee(9, "営業", STAFF))).isFalse();
    }

    @Test
    void 課長と部長の本人承認は1万円未満だけ() { // 2.14
        Employee sectionManager = employee(2, "営業", SECTION_MANAGER);
        assertThat(policy.isEligibleApprover(pending(2, 9_999, SECTION_MANAGER), sectionManager, sectionManager)).isTrue();
        assertThat(policy.isEligibleApprover(pending(2, 10_000, SECTION_MANAGER), sectionManager, sectionManager)).isFalse();
        assertThat(policy.isEligibleApprover(pending(2, 50_000, SECTION_MANAGER), sectionManager, sectionManager)).isFalse();

        Employee departmentManager = employee(3, "営業", DEPARTMENT_MANAGER);
        assertThat(policy.isEligibleApprover(pending(3, 9_999, DEPARTMENT_MANAGER), departmentManager, departmentManager)).isTrue();
        assertThat(policy.isEligibleApprover(pending(3, 10_000, DEPARTMENT_MANAGER), departmentManager, departmentManager)).isFalse();
        assertThat(policy.isEligibleApprover(pending(3, 2_000_000, DEPARTMENT_MANAGER), departmentManager, departmentManager)).isFalse();
    }
}
