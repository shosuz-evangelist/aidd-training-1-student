package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.dto.HistoryView;
import com.example.ecsite.approval.dto.ItemInput;
import com.example.ecsite.approval.dto.PurchaseRequestView;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.HistoryAction;
import com.example.ecsite.approval.exception.RequestNotFoundException;
import com.example.ecsite.approval.service.PurchaseRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 要件 9.1, 9.3 - 履歴の一覧。 */
@SpringBootTest
@Import(TestClockConfig.class)
class PurchaseRequestHistoryTest {
    @Autowired PurchaseRequestService service;
    @Autowired TestDataHelper data;
    @Autowired MutableClock clock;

    long staff;
    long sm;
    long dm;

    @BeforeEach
    void setUp() {
        data.reset();
        staff = emp("staff", EmployeeRole.STAFF);
        sm = emp("sm", EmployeeRole.SECTION_MANAGER);
        dm = emp("dm", EmployeeRole.DEPARTMENT_MANAGER);
    }

    private long emp(String name, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), "営業部", role);
    }

    @Test
    void 承認を2回行った申請の履歴が古い順に2件返り日時は日本時間で代理元は空() {
        PurchaseRequestView v = service.submit(staff, List.of(new ItemInput(1_000_001, 1)));
        clock.set(OffsetDateTime.parse("2026-10-20T01:00:00Z"));
        service.approve(v.id(), sm);
        clock.set(OffsetDateTime.parse("2026-10-21T15:30:00Z"));
        service.approve(v.id(), dm);

        List<HistoryView> history = service.history(v.id());

        assertThat(history).hasSize(2);
        assertThat(history).extracting(HistoryView::actorId).containsExactly(sm, dm);
        assertThat(history).extracting(HistoryView::action).containsOnly(HistoryAction.APPROVE);
        assertThat(history).extracting(HistoryView::onBehalfOfId).containsOnlyNulls();
        assertThat(history).extracting(HistoryView::reason).containsOnlyNulls();
        assertThat(history.get(0).actedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(history.get(1).actedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(history.get(0).actedAt().toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-20T01:00:00Z").toInstant());
        assertThat(history.get(1).actedAt()).isEqualTo(OffsetDateTime.parse("2026-10-22T00:30:00+09:00"));
    }

    @Test
    void 履歴のない申請は空の一覧で存在しない申請は404() {
        PurchaseRequestView v = service.submit(staff, List.of(new ItemInput(100, 1)));

        assertThat(service.history(v.id())).isEmpty();
        assertThatThrownBy(() -> service.history(v.id() + 1000)).isInstanceOf(RequestNotFoundException.class);
    }
}
