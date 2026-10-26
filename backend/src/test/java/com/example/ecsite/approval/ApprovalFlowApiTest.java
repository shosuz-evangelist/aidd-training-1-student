package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.entity.EmployeeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 2.6, 3.1, 3.2, 3.3, 4.6, 6.1, 7.2, 9.1, 9.2, 9.3 - 申請から発注までの全体の流れを HTTP の API だけで確かめる。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class ApprovalFlowApiTest {
    static final String BASE = "/api/approval/requests";
    static final String NOW = "2026-10-21T10:30:00+09:00";

    @Autowired MockMvc mvc;
    @Autowired TestDataHelper data;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper json;

    long staff;
    long sm;
    long dm;
    long delegate;
    long executive;
    long purchasing;

    @BeforeEach
    void setUp() {
        data.reset();
        clock.set(OffsetDateTime.parse(NOW));
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        dm = emp("dm", "営業部", EmployeeRole.DEPARTMENT_MANAGER);
        delegate = emp("delegate", "総務部", EmployeeRole.STAFF);
        executive = emp("exec", null,EmployeeRole.EXECUTIVE);
        purchasing = emp("pur", "購買部", EmployeeRole.PURCHASING);
    }

    private long emp(String name, String dept, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), dept, role);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long submit(long applicant, long unitPrice) throws Exception {
        ResultActions r = mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicantId\":" + applicant + ",\"items\":[{\"unitPrice\":" + unitPrice + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated());
        return body(r).get("id").asLong();
    }

    private void act(String op, long id, long actorId) throws Exception {
        mvc.perform(post(BASE + "/" + id + "/" + op).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":" + actorId + "}")).andExpect(status().isOk());
    }

    private void giveBack(long id, long actorId, String reason) throws Exception {
        mvc.perform(post(BASE + "/" + id + "/return").contentType(MediaType.APPLICATION_JSON)
                .content("{\"actorId\":" + actorId + ",\"reason\":\"" + reason + "\"}")).andExpect(status().isOk());
    }

    private void delegateFromTo(long delegator, long to) throws Exception {
        mvc.perform(post("/api/approval/delegations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"delegatorId\":" + delegator + ",\"delegateId\":" + to
                        + ",\"startDate\":\"2026-10-20\",\"endDate\":\"2026-10-22\"}"))
                .andExpect(status().is2xxSuccessful());
    }

    /** 一覧の API から、指定した申請の状態と承認待ちの役割を読む。 */
    private String stateInList(long id) throws Exception {
        JsonNode list = body(mvc.perform(get(BASE).param("viewerId", String.valueOf(staff))).andExpect(status().isOk()));
        for (JsonNode n : list) {
            if (n.get("id").asLong() == id) {
                return n.get("status").asString() + "/" + (n.get("pendingRole").isNull() ? "null" : n.get("pendingRole").asString());
            }
        }
        throw new AssertionError("一覧に申請がない: " + id);
    }

    private JsonNode history(long id) throws Exception {
        return body(mvc.perform(get(BASE + "/" + id + "/history")).andExpect(status().isOk()));
    }

    private void assertEntry(JsonNode e, long actor, Long onBehalfOf, String action, String reason) {
        assertThat(e.get("actorId").asLong()).isEqualTo(actor);
        if (onBehalfOf == null) {
            assertThat(e.get("onBehalfOfId").isNull()).isTrue();
        } else {
            assertThat(e.get("onBehalfOfId").asLong()).isEqualTo(onBehalfOf);
        }
        assertThat(e.get("action").asString()).isEqualTo(action);
        assertThat(OffsetDateTime.parse(e.get("actedAt").asString()).toInstant())
                .isEqualTo(OffsetDateTime.parse(NOW).toInstant());
        if (reason == null) {
            assertThat(e.get("reason") == null || e.get("reason").isNull()).isTrue();
        } else {
            assertThat(e.get("reason").asString()).isEqualTo(reason);
        }
    }

    @Test
    void 百万一円の申請が課長_部長の代理人_役員の承認を経て購買部の発注まで進み履歴が4件残る() throws Exception {
        delegateFromTo(dm, delegate);

        long id = submit(staff, 1_000_001);
        assertThat(stateInList(id)).isEqualTo("PENDING/SECTION_MANAGER");

        act("approve", id, sm);
        assertThat(stateInList(id)).isEqualTo("PENDING/DEPARTMENT_MANAGER");

        act("approve", id, delegate);
        assertThat(stateInList(id)).isEqualTo("PENDING/EXECUTIVE");

        act("approve", id, executive);
        assertThat(stateInList(id)).isEqualTo("APPROVED/null");

        act("order", id, purchasing);
        assertThat(stateInList(id)).isEqualTo("ORDERED/null");

        JsonNode h = history(id);
        assertThat(h.size()).isEqualTo(4);
        assertEntry(h.get(0), sm, null, "APPROVE", null);
        assertEntry(h.get(1), delegate, dm, "APPROVE", null);
        assertEntry(h.get(2), executive, null, "APPROVE", null);
        assertEntry(h.get(3), purchasing, null, "ORDER", null);
    }

    @Test
    void 二段階目の部長が理由つきで差し戻すと差し戻しになり先の課長の承認の履歴が残る() throws Exception {
        long id = submit(staff, 1_000_001);
        act("approve", id, sm);
        assertThat(stateInList(id)).isEqualTo("PENDING/DEPARTMENT_MANAGER");

        giveBack(id, dm, "金額の根拠が不足");
        assertThat(stateInList(id)).isEqualTo("RETURNED/null");

        JsonNode h = history(id);
        assertThat(h.size()).isEqualTo(2);
        assertEntry(h.get(0), sm, null, "APPROVE", null);
        assertEntry(h.get(1), dm, null, "RETURN", "金額の根拠が不足");
    }
}
