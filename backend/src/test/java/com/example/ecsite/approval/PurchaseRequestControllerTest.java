package com.example.ecsite.approval;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.TestDataHelper.Item;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.RequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 1.4, 1.5, 1.6, 4.2, 5.2, 6.2, 8.4, 9.5 - 購買申請の API(7 つのエンドポイント)。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class PurchaseRequestControllerTest {
    static final String BASE = "/api/approval/requests";

    @Autowired MockMvc mvc;
    @Autowired TestDataHelper data;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper json;

    long staff;
    long sm;
    long otherSm;
    long dm;
    long purchasing;

    @BeforeEach
    void setUp() {
        data.reset();
        clock.set(OffsetDateTime.parse("2026-10-21T10:30:00+09:00"));
        staff = emp("staff", "営業部", EmployeeRole.STAFF);
        sm = emp("sm", "営業部", EmployeeRole.SECTION_MANAGER);
        otherSm = emp("othersm", "総務部", EmployeeRole.SECTION_MANAGER);
        dm = emp("dm", "営業部", EmployeeRole.DEPARTMENT_MANAGER);
        purchasing = emp("pur", "購買部", EmployeeRole.PURCHASING);
    }

    private long emp(String name, String dept, EmployeeRole role) {
        return data.createEmployee(data.createCustomer(name + "@example.com"), dept, role);
    }

    private long pending() {
        return data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions act(String op, long id, long actorId) throws Exception {
        return postJson(BASE + "/" + id + "/" + op, "{\"actorId\":" + actorId + "}");
    }

    private ResultActions giveBack(long id, long actorId, String reason) throws Exception {
        return postJson(BASE + "/" + id + "/return", "{\"actorId\":" + actorId + ",\"reason\":\"" + reason + "\"}");
    }

    private String snapshot() {
        return jdbc.queryForList("SELECT id, status, pending_role FROM purchase_request ORDER BY id").toString()
                + jdbc.queryForObject("SELECT count(*) FROM approval_history", Integer.class);
    }

    // --- 正常な応答 ---

    @Test
    void 申請すると201で承認待ちの申請が返る() throws Exception {
        postJson(BASE, "{\"applicantId\":" + staff + ",\"items\":[{\"unitPrice\":1000,\"quantity\":2}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.applicantId").value(staff))
                .andExpect(jsonPath("$.totalAmount").value(2000))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pendingRole").value("SECTION_MANAGER"));
    }

    @Test
    void 承認すると200で次の状態の申請が返り履歴が1件できる() throws Exception {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000_000, 1));

        act("approve", id, sm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pendingRole").value("DEPARTMENT_MANAGER"));

        mvc.perform(get(BASE + "/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actorId").value(sm))
                .andExpect(jsonPath("$[0].action").value("APPROVE"))
                .andExpect(jsonPath("$[0].onBehalfOfId").value(nullValue()));
    }

    @Test
    void 理由をつけて差し戻すと200で差し戻しの申請が返り履歴に理由が残る() throws Exception {
        long id = pending();

        giveBack(id, sm, "金額の根拠が不足")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.pendingRole").value(nullValue()));

        mvc.perform(get(BASE + "/" + id + "/history"))
                .andExpect(jsonPath("$[0].action").value("RETURN"))
                .andExpect(jsonPath("$[0].reason").value("金額の根拠が不足"));
    }

    @Test
    void 購買部が発注すると200で発注済みの申請が返る() throws Exception {
        long id = data.createRequest(staff, RequestStatus.APPROVED, null, new Item(1_000, 1));

        act("order", id, purchasing)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"));
    }

    @Test
    void 一覧は閲覧者ごとの可否つきでid昇順に返る() throws Exception {
        long first = pending();
        long second = data.createRequest(staff, RequestStatus.RETURNED, null, new Item(500, 2));

        mvc.perform(get(BASE).param("viewerId", String.valueOf(sm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].canApprove").value(true))
                .andExpect(jsonPath("$[0].canReturn").value(true))
                .andExpect(jsonPath("$[0].canResubmit").value(false))
                .andExpect(jsonPath("$[0].canOrder").value(false))
                .andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[1].canApprove").value(false));

        mvc.perform(get(BASE).param("viewerId", String.valueOf(staff)))
                .andExpect(jsonPath("$[1].canResubmit").value(true));
    }

    @Test
    void 履歴は古い順に返る() throws Exception {
        long id = data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000_000, 1));
        act("approve", id, sm).andExpect(status().isOk());
        giveBack(id, dm, "再検討").andExpect(status().isOk());

        mvc.perform(get(BASE + "/" + id + "/history"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].action").value("APPROVE"))
                .andExpect(jsonPath("$[1].action").value("RETURN"));
    }

    // --- エラーの応答 ---

    @Test
    void 存在しない申請は404でコードがない() throws Exception {
        act("approve", 999, sm).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.code").doesNotExist());
        giveBack(999, sm, "理由").andExpect(status().isNotFound());
        act("resubmit", 999, staff).andExpect(status().isNotFound());
        act("order", 999, purchasing).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/999/history")).andExpect(status().isNotFound());
    }

    @Test
    void 存在しない社員は404() throws Exception {
        long id = pending();
        act("approve", id, 999).andExpect(status().isNotFound());
        mvc.perform(get(BASE).param("viewerId", "999")).andExpect(status().isNotFound());
        postJson(BASE, "{\"applicantId\":999,\"items\":[{\"unitPrice\":1000,\"quantity\":1}]}")
                .andExpect(status().isNotFound());
    }

    @Test
    void 明細の不正は400でコードがない() throws Exception {
        String[] bodies = {
                "{\"applicantId\":" + staff + ",\"items\":[]}",
                "{\"applicantId\":" + staff + "}",
                "{\"applicantId\":" + staff + ",\"items\":[{\"unitPrice\":1000,\"quantity\":0}]}",
                "{\"applicantId\":" + staff + ",\"items\":[{\"unitPrice\":-1,\"quantity\":1}]}",
                "{\"items\":[{\"unitPrice\":1000,\"quantity\":1}]}",
                "{\"applicantId\":" + staff + ",\"items\":[{\"quantity\":1}]}",
                "{\"applicantId\":" + staff + ",\"items\":[{\"unitPrice\":1000}]}",
                "{\"applicantId\":" + staff + ",\"items\":[null]}",
        };
        for (String body : bodies) {
            postJson(BASE, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.code").doesNotExist());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_request", Integer.class)).isZero();
    }

    @Test
    void 単価0円の明細は受け付けて合計0円になる() throws Exception {
        postJson(BASE, "{\"applicantId\":" + staff + ",\"items\":[{\"unitPrice\":0,\"quantity\":1}]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    void 操作者の指定がないか壊れた入力は400で500にならない()throws Exception {
        long id = pending();
        postJson(BASE + "/" + id + "/approve", "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist());
        postJson(BASE + "/" + id + "/order", "{}").andExpect(status().isBadRequest());
        postJson(BASE + "/" + id + "/resubmit", "{}").andExpect(status().isBadRequest());
        postJson(BASE + "/" + id + "/return", "{\"reason\":\"x\"}").andExpect(status().isBadRequest());
        postJson(BASE + "/" + id + "/approve", "{broken").andExpect(status().isBadRequest());
        postJson(BASE, "not json").andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + id + "/approve")).andExpect(status().is4xxClientError());
        mvc.perform(get(BASE)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("viewerId", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/abc/history")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_history", Integer.class)).isZero();
    }

    @Test
    void 権限のない承認はA01で403() throws Exception {
        long id = pending();
        act("approve", id, otherSm).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("A01"));
    }

    @Test
    void 空白だけの理由の差し戻しはA02で400() throws Exception {
        long id = pending();
        giveBack(id, sm, "  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("A02"));
        postJson(BASE + "/" + id + "/return", "{\"actorId\":" + sm + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("A02"));
    }

    @Test
    void 承認待ちでない申請の承認はA03で409() throws Exception {
        long id = data.createRequest(staff, RequestStatus.APPROVED, null, new Item(1_000, 1));
        act("approve", id, sm).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("A03"));
    }

    @Test
    void 出し直しは申請者でなければA01で403() throws Exception {
        long id = data.createRequest(staff, RequestStatus.RETURNED, null, new Item(1_000, 1));
        act("resubmit", id, sm).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("A01"));
    }

    @Test
    void 出し直しは差し戻しでなければA03で409() throws Exception {
        long id = pending();
        act("resubmit", id, staff).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("A03"));
    }

    @Test
    void 出し直しの確認に通ってもまだ成功の応答は定めず状態も履歴も変えない() throws Exception {
        long id = data.createRequest(staff, RequestStatus.RETURNED, null, new Item(1_000, 1));
        String before = snapshot();

        act("resubmit", id, staff).andExpect(status().isNotImplemented()).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.code").doesNotExist());

        assertThat(snapshot()).isEqualTo(before);
    }

    // --- 要件 8.4 一覧で操作できないとされた操作を API で直接呼ぶ ---

    @Test
    void 一覧で操作できない操作を直接呼ぶとA01かA03で拒否され状態も履歴も変わらない() throws Exception {
        data.createRequest(staff, RequestStatus.PENDING, EmployeeRole.SECTION_MANAGER, new Item(1_000, 1));
        data.createRequest(staff, RequestStatus.RETURNED, null, new Item(1_000, 1));
        data.createRequest(staff, RequestStatus.APPROVED, null, new Item(1_000, 1));
        data.createRequest(staff, RequestStatus.ORDERED, null, new Item(1_000, 1));
        Map<String, Integer> denied = new java.util.HashMap<>(Map.of("approve", 0, "return", 0, "resubmit", 0, "order", 0));
        Map<String, Integer> allowed = new java.util.HashMap<>(Map.of("approve", 0, "return", 0, "resubmit", 0, "order", 0));
        String before = snapshot();

        for (long viewer : List.of(staff, sm, otherSm, dm, purchasing)) {
            JsonNode items = json.readTree(mvc.perform(get(BASE).param("viewerId", String.valueOf(viewer)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            for (JsonNode item : items) {
                long id = item.get("id").asLong();
                for (String op : List.of("approve", "return", "resubmit", "order")) {
                    String flag = "can" + Character.toUpperCase(op.charAt(0)) + op.substring(1);
                    if (item.get(flag).asBoolean()) {
                        allowed.merge(op, 1, Integer::sum);
                        continue;
                    }
                    denied.merge(op, 1, Integer::sum);
                    ResultActions result = op.equals("return") ? giveBack(id, viewer, "理由") : act(op, id, viewer);
                    String body = result.andReturn().getResponse().getContentAsString();
                    int httpStatus = result.andReturn().getResponse().getStatus();
                    String code = json.readTree(body).get("code").asText();
                    assertThat(code).as("viewer %d request %d %s", viewer, id, op).isIn("A01", "A03");
                    assertThat(httpStatus).isEqualTo(code.equals("A01") ? 403 : 409);
                    assertThat(snapshot()).as("viewer %d request %d %s", viewer, id, op).isEqualTo(before);
                }
            }
        }
        // 4 つの操作のどれも、拒否を確かめた件と、可と示された件の両方がある
        for (String op : List.of("approve", "return", "resubmit", "order")) {
            assertThat(denied.get(op)).as(op).isPositive();
            assertThat(allowed.get(op)).as(op).isPositive();
        }
    }
}
