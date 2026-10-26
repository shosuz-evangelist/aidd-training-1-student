package com.example.ecsite.approval;

import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.entity.HistoryAction;
import com.example.ecsite.approval.entity.ApprovalHistory;
import com.example.ecsite.approval.entity.Delegation;
import com.example.ecsite.approval.entity.Employee;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.PurchaseRequest;
import com.example.ecsite.approval.entity.PurchaseRequestItem;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.repository.ApprovalHistoryRepository;
import com.example.ecsite.approval.repository.DelegationRepository;
import com.example.ecsite.approval.repository.EmployeeRepository;
import com.example.ecsite.approval.repository.PurchaseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 購買承認の 5 つのエンティティを保存し、読み戻せることを確かめる。 */
@SpringBootTest
class ApprovalEntityTest {
    @Autowired TestDataHelper data;
    @Autowired EmployeeRepository employees;
    @Autowired PurchaseRequestRepository requests;
    @Autowired ApprovalHistoryRepository histories;
    @Autowired DelegationRepository delegations;
    @Autowired TransactionTemplate tx;

    long staff;
    long manager;

    @BeforeEach
    void setUp() {
        data.reset();
        staff = data.createCustomer("staff@example.com");
        manager = data.createCustomer("manager@example.com");
        employees.save(new Employee(staff, "営業", EmployeeRole.STAFF));
        employees.save(new Employee(manager, "営業", EmployeeRole.SECTION_MANAGER));
    }

    @Test
    void 社員を保存して読み戻せる_役員は部署がない() {
        long exec = data.createCustomer("exec@example.com");
        employees.save(new Employee(exec, null, EmployeeRole.EXECUTIVE));

        Employee s = employees.findById(staff).orElseThrow();
        assertThat(s.getDepartment()).isEqualTo("営業");
        assertThat(s.getRole()).isEqualTo(EmployeeRole.STAFF);
        Employee e = employees.findById(exec).orElseThrow();
        assertThat(e.getDepartment()).isNull();
        assertThat(e.getRole()).isEqualTo(EmployeeRole.EXECUTIVE);
    }

    @Test
    void 申請と明細を1つの集約として保存して読み戻せる() {
        PurchaseRequest request = new PurchaseRequest(staff, 2500, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                OffsetDateTime.parse("2026-10-01T09:00:00+09:00"));
        request.addItem(new PurchaseRequestItem(500, 3));
        request.addItem(new PurchaseRequestItem(1000, 1));
        long id = requests.save(request).getId();

        List<PurchaseRequestItem> items = tx.execute(s -> requests.findById(id).orElseThrow().getItems().stream().toList());
        PurchaseRequest loaded = requests.findById(id).orElseThrow();
        assertThat(loaded.getApplicantId()).isEqualTo(staff);
        assertThat(loaded.getTotalAmount()).isEqualTo(2500);
        assertThat(loaded.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(loaded.getPendingRole()).isEqualTo(EmployeeRole.SECTION_MANAGER);
        assertThat(loaded.getCreatedAt().toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-01T09:00:00+09:00").toInstant());
        assertThat(items).extracting(PurchaseRequestItem::getUnitPrice).containsExactlyInAnyOrder(500L, 1000L);
        assertThat(items).extracting(PurchaseRequestItem::getQuantity).containsExactlyInAnyOrder(3, 1);
    }

    @Test
    void 履歴を追記して読み戻せる() {
        PurchaseRequest request = new PurchaseRequest(staff, 100, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER,
                OffsetDateTime.parse("2026-10-01T09:00:00+09:00"));
        request.addItem(new PurchaseRequestItem(100, 1));
        long requestId = requests.save(request).getId();

        OffsetDateTime at = OffsetDateTime.parse("2026-10-02T10:00:00+09:00");
        long historyId = histories.save(new ApprovalHistory(requestId, manager, null, HistoryAction.RETURN, "数量の見直し", at)).getId();

        ApprovalHistory h = histories.findById(historyId).orElseThrow();
        assertThat(h.getRequestId()).isEqualTo(requestId);
        assertThat(h.getActorId()).isEqualTo(manager);
        assertThat(h.getOnBehalfOfId()).isNull();
        assertThat(h.getAction()).isEqualTo(HistoryAction.RETURN);
        assertThat(h.getReason()).isEqualTo("数量の見直し");
        assertThat(h.getActedAt().toInstant()).isEqualTo(at.toInstant());
    }

    @Test
    void 代理を保存して読み戻せる() {
        long id = delegations.save(new Delegation(manager, staff, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"))).getId();

        Delegation d = delegations.findById(id).orElseThrow();
        assertThat(d.getDelegatorId()).isEqualTo(manager);
        assertThat(d.getDelegateId()).isEqualTo(staff);
        assertThat(d.getStartDate()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(d.getEndDate()).isEqualTo(LocalDate.parse("2026-10-07"));
    }
}
