package com.example.ecsite.approval;

import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.dto.ItemInput;
import com.example.ecsite.approval.dto.PurchaseRequestView;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.exception.EmployeeNotFoundException;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import com.example.ecsite.approval.service.PurchaseRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 1.1-1.6, 2.1, 2.7, 2.12 - 購買の申請。 */
@SpringBootTest
class PurchaseRequestSubmitTest {
    @Autowired PurchaseRequestService service;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;

    long staff;
    long sectionManager;
    long departmentManager;

    @BeforeEach
    void setUp() {
        data.reset();
        staff = data.createEmployee(data.createCustomer("staff@example.com"), "営業部", EmployeeRole.STAFF);
        sectionManager = data.createEmployee(data.createCustomer("sm@example.com"), "営業部", EmployeeRole.SECTION_MANAGER);
        departmentManager = data.createEmployee(data.createCustomer("dm@example.com"), "営業部", EmployeeRole.DEPARTMENT_MANAGER);
    }

    private int requestCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM purchase_request", Integer.class);
    }

    private int itemCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM purchase_request_item", Integer.class);
    }

    private int historyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM approval_history", Integer.class);
    }

    @Test
    void 担当者の1000円3個は3000円で課長の承認待ちになる() {
        PurchaseRequestView view = service.submit(staff, List.of(new ItemInput(1_000, 3)));

        assertThat(view.totalAmount()).isEqualTo(3_000);
        assertThat(view.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(view.pendingRole()).isEqualTo(EmployeeRole.SECTION_MANAGER);
        assertThat(view.applicantId()).isEqualTo(staff);
        assertThat(jdbc.queryForMap("SELECT total_amount, status, pending_role FROM purchase_request WHERE id = ?", view.id()))
                .containsEntry("total_amount", 3_000L)
                .containsEntry("status", "PENDING")
                .containsEntry("pending_role", "SECTION_MANAGER");
        assertThat(itemCount()).isEqualTo(1);
        assertThat(historyCount()).isZero();
    }

    @Test
    void 複数の明細の合計を申請金額にして明細を全て保存する() {
        PurchaseRequestView view = service.submit(staff, List.of(new ItemInput(500, 2), new ItemInput(2_000, 1)));

        assertThat(view.totalAmount()).isEqualTo(3_000);
        assertThat(itemCount()).isEqualTo(2);
    }

    @Test
    void 単価0円の申請は0円として受け付ける() {
        PurchaseRequestView view = service.submit(staff, List.of(new ItemInput(0, 1)));

        assertThat(view.totalAmount()).isZero();
        assertThat(view.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(view.pendingRole()).isEqualTo(EmployeeRole.SECTION_MANAGER);
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void 明細なしは拒否して何も保存しない() {
        assertThatThrownBy(() -> service.submit(staff, List.of())).isInstanceOf(InvalidApprovalRequestException.class);
        assertThat(requestCount()).isZero();
    }

    @Test
    void 明細がnullでも拒否して何も保存しない() {
        assertThatThrownBy(() -> service.submit(staff, null)).isInstanceOf(InvalidApprovalRequestException.class);
        assertThat(requestCount()).isZero();
    }

    @Test
    void 数量0は拒否して何も保存しない() {
        assertThatThrownBy(() -> service.submit(staff, List.of(new ItemInput(1_000, 1), new ItemInput(1_000, 0))))
                .isInstanceOf(InvalidApprovalRequestException.class);
        assertThat(requestCount()).isZero();
        assertThat(itemCount()).isZero();
    }

    @Test
    void 単価マイナスは拒否して何も保存しない() {
        assertThatThrownBy(() -> service.submit(staff, List.of(new ItemInput(-1, 1))))
                .isInstanceOf(InvalidApprovalRequestException.class);
        assertThat(requestCount()).isZero();
    }

    @Test
    void 合計が桁あふれするときは拒否して何も保存しない() {
        assertThatThrownBy(() -> service.submit(staff, List.of(new ItemInput(Long.MAX_VALUE, 2))))
                .isInstanceOf(InvalidApprovalRequestException.class);
        assertThat(requestCount()).isZero();
    }

    @Test
    void 課長の9999円は課長本人の承認待ちになる() {
        PurchaseRequestView view = service.submit(sectionManager, List.of(new ItemInput(9_999, 1)));

        assertThat(view.pendingRole()).isEqualTo(EmployeeRole.SECTION_MANAGER);
        assertThat(view.status()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void 部長の9999円は部長本人の承認待ちになる() {
        PurchaseRequestView view = service.submit(departmentManager, List.of(new ItemInput(9_999, 1)));

        assertThat(view.pendingRole()).isEqualTo(EmployeeRole.DEPARTMENT_MANAGER);
    }

    @Test
    void 役員と購買部は申請できず何も保存しない() {
        long executive = data.createEmployee(data.createCustomer("ex@example.com"), null, EmployeeRole.EXECUTIVE);
        long purchasing = data.createEmployee(data.createCustomer("pu@example.com"), "購買部", EmployeeRole.PURCHASING);

        assertThatThrownBy(() -> service.submit(executive, List.of(new ItemInput(1_000, 1))))
                .isInstanceOf(InvalidApprovalRequestException.class);
        assertThatThrownBy(() -> service.submit(purchasing, List.of(new ItemInput(1_000, 1))))
                .isInstanceOf(InvalidApprovalRequestException.class);
        assertThat(requestCount()).isZero();
        assertThat(historyCount()).isZero();
    }

    @Test
    void 申請者がいないときは拒否して何も保存しない() {
        assertThatThrownBy(() -> service.submit(999_999, List.of(new ItemInput(1_000, 1))))
                .isInstanceOf(EmployeeNotFoundException.class);
        assertThat(requestCount()).isZero();
    }
}
