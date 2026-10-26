package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.dto.DelegationView;
import com.example.ecsite.approval.entity.Delegation;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.service.DelegationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 7.1, 7.3, 7.4, 7.5 - 代理の登録と、両端を含む期間での有効な代理の検索。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class DelegationTest {
    static final LocalDate START = LocalDate.of(2026, 10, 14);
    static final LocalDate END = LocalDate.of(2026, 10, 16);

    @Autowired DelegationService service;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestDataHelper data;
    @Autowired MutableClock clock;

    long delegator;
    long delegate;

    @BeforeEach
    void setUp() {
        data.reset();
        delegator = data.createEmployee(data.createCustomer("boss@example.com"), "営業部", EmployeeRole.SECTION_MANAGER);
        delegate = data.createEmployee(data.createCustomer("deputy@example.com"), "営業部", EmployeeRole.STAFF);
    }

    private static OffsetDateTime at(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + "+09:00");
    }

    private List<Delegation> activeAt(String localDateTime) {
        clock.set(at(localDateTime));
        return service.activeFor(delegate, LocalDate.now(clock));
    }

    private String body(long delegatorId, long delegateId) {
        return "{\"delegatorId\":%d,\"delegateId\":%d,\"startDate\":\"2026-10-14\",\"endDate\":\"2026-10-16\"}"
                .formatted(delegatorId, delegateId);
    }

    @Test
    void 登録すると代理が保存されAPIは201を返す() throws Exception {
        mvc.perform(post("/api/approval/delegations").contentType(MediaType.APPLICATION_JSON)
                        .content(body(delegator, delegate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delegatorId").value(delegator))
                .andExpect(jsonPath("$.delegateId").value(delegate))
                .andExpect(jsonPath("$.startDate").value("2026-10-14"))
                .andExpect(jsonPath("$.endDate").value("2026-10-16"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM delegation WHERE delegator_id = ? AND delegate_id = ?"
                + " AND start_date = DATE '2026-10-14' AND end_date = DATE '2026-10-16'",
                Integer.class, delegator, delegate)).isEqualTo(1);
    }

    @Test
    void 代理元がいなければ404() throws Exception {
        mvc.perform(post("/api/approval/delegations").contentType(MediaType.APPLICATION_JSON)
                        .content(body(9999, delegate)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delegation", Integer.class)).isZero();
    }

    @Test
    void 代理人がいなければ404() throws Exception {
        mvc.perform(post("/api/approval/delegations").contentType(MediaType.APPLICATION_JSON)
                        .content(body(delegator, 9999)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM delegation", Integer.class)).isZero();
    }

    @Test
    void registerは保存した内容を返す() {
        DelegationView view = service.register(delegator, delegate, START, END);

        assertThat(view.id()).isNotNull();
        assertThat(view.delegatorId()).isEqualTo(delegator);
        assertThat(view.delegateId()).isEqualTo(delegate);
        assertThat(view.startDate()).isEqualTo(START);
        assertThat(view.endDate()).isEqualTo(END);
    }

    @Test
    void 開始日の前日は無効() {
        service.register(delegator, delegate, START, END);
        assertThat(activeAt("2026-10-13T23:59:59")).isEmpty();
    }

    @Test
    void 開始日の0時は有効() {
        service.register(delegator, delegate, START, END);
        List<Delegation> active = activeAt("2026-10-14T00:00:00");
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getDelegatorId()).isEqualTo(delegator);
    }

    @Test
    void 終了日の23時59分は有効() {
        service.register(delegator, delegate, START, END);
        assertThat(activeAt("2026-10-16T23:59:00")).hasSize(1);
    }

    @Test
    void 終了日の翌日は無効() {
        service.register(delegator, delegate, START, END);
        assertThat(activeAt("2026-10-17T00:00:00")).isEmpty();
    }

    @Test
    void 別の代理人の代理は含まれない() {
        long other = data.createEmployee(data.createCustomer("other@example.com"), "営業部", EmployeeRole.STAFF);
        service.register(delegator, other, START, END);
        assertThat(activeAt("2026-10-15T12:00:00")).isEmpty();
    }
}
