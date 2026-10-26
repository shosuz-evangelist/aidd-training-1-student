package com.example.ecsite.reservation;

import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.CourseSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 1 と 8 - 講座の登録（基本の検査と保存） */
@AutoConfigureMockMvc
class CourseRegistrationTest extends ReservationTestBase {
    @Autowired MockMvc mvc;

    private static String sessionJson(String startsAt, String endsAt, String room, String instructor) {
        return """
                {"startsAt":"%s","endsAt":"%s","room":"%s","instructor":"%s"}"""
                .formatted(startsAt, endsAt, room, instructor);
    }

    private static String courseJson(String kind, int capacity, String... sessions) {
        return """
                {"name":"入門書道","category":"書道","kind":"%s","price":12000,"capacity":%d,"sessions":[%s]}"""
                .formatted(kind, capacity, String.join(",", sessions));
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/reservation/courses").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertNothingSaved() {
        assertThat(courseRepository.count()).isZero();
        assertThat(courseSessionRepository.count()).isZero();
    }

    @Test
    void 要件1_3_8_7_正しい単発の入力は講座と第1回を保存して201で返す() throws Exception {
        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("入門書道"))
                .andExpect(jsonPath("$.category").value("書道"))
                .andExpect(jsonPath("$.kind").value("SINGLE"))
                .andExpect(jsonPath("$.price").value(12000))
                .andExpect(jsonPath("$.remaining").value(10))
                .andExpect(jsonPath("$.seatLabel").value("残り 10 席"))
                .andExpect(jsonPath("$.reservable").value(true))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].startsAt").value("2026-11-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.sessions[0].endsAt").value("2026-11-01T12:00:00+09:00"))
                .andExpect(jsonPath("$.sessions[0].room").value("A教室"))
                .andExpect(jsonPath("$.sessions[0].instructor").value("山田"));

        List<Course> courses = courseRepository.findAllWithSessions();
        assertThat(courses).hasSize(1);
        Course saved = courses.get(0);
        assertThat(saved.getName()).isEqualTo("入門書道");
        assertThat(saved.getCategory()).isEqualTo("書道");
        assertThat(saved.getPrice()).isEqualTo(12000L);
        assertThat(saved.getCapacity()).isEqualTo(10);
        assertThat(saved.getReservedCount()).isZero();
        assertThat(saved.getSessions()).hasSize(1);
        CourseSession session = saved.getSessions().get(0);
        assertThat(session.getSessionNo()).isEqualTo(1);
        assertThat(session.getStartsAt()).isEqualTo(at("2026-11-01T10:00"));
        assertThat(session.getEndsAt()).isEqualTo(at("2026-11-01T12:00"));
        assertThat(session.getRoom()).isEqualTo("A教室");
        assertThat(session.getInstructor()).isEqualTo("山田");
    }

    @Test
    void 要件8_2_開始が終了より後ならR07を返し何も保存しない() throws Exception {
        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T12:00:00+09:00", "2026-11-01T10:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"))
                .andExpect(jsonPath("$.message").value("入力が正しくありません"));

        assertNothingSaved();
    }

    @Test
    void 要件8_2_開始と終了が同じ時刻でもR07を返し何も保存しない() throws Exception {
        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T10:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"));

        assertNothingSaved();
    }

    @Test
    void 要件8_2_いずれかの回の開始が終了以降ならR07を返し何も保存しない() throws Exception {
        register(courseJson("SERIES", 10,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田"),
                sessionJson("2026-11-08T12:00:00+09:00", "2026-11-08T10:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"));

        assertNothingSaved();
    }

    @Test
    void 要件8_3_定員が0ならR07を返し何も保存しない() throws Exception {
        register(courseJson("SINGLE", 0,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"))
                .andExpect(jsonPath("$.message").value("入力が正しくありません"));

        assertNothingSaved();
    }

    @Test
    void 要件8_3_定員が負ならR07を返し何も保存しない() throws Exception {
        register(courseJson("SINGLE", -1,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"));

        assertNothingSaved();
    }

    @Test
    void 要件8_3_定員が1ちょうどなら登録できる() throws Exception {
        register(courseJson("SINGLE", 1,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remaining").value(1));

        assertThat(courseRepository.count()).isEqualTo(1);
    }

    @Test
    void 要件8_1_開始と終了の誤りと定員の誤りが両方あっても8_1の順で最初の失敗のR07を返し何も保存しない() throws Exception {
        register(courseJson("SINGLE", 0,
                sessionJson("2026-11-01T12:00:00+09:00", "2026-11-01T10:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"));

        assertNothingSaved();
    }

    @Test
    void 要件8_7_連続講座の回は入力の順に1から番号を振って保存する() throws Exception {
        register(courseJson("SERIES", 10,
                sessionJson("2026-11-08T10:00:00+09:00", "2026-11-08T12:00:00+09:00", "A教室", "山田"),
                sessionJson("2026-11-15T10:00:00+09:00", "2026-11-15T12:00:00+09:00", "B教室", "佐藤")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].room").value("A教室"))
                .andExpect(jsonPath("$.sessions[1].room").value("B教室"));

        Course saved = courseRepository.findAllWithSessions().get(0);
        assertThat(saved.getSessions()).extracting(CourseSession::getSessionNo).containsExactly(1, 2);
        assertThat(saved.getSessions()).extracting(CourseSession::getRoom).containsExactly("A教室", "B教室");
    }

    private void assertOnlyExistingSaved() {
        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(courseSessionRepository.count()).isEqualTo(1);
    }

    private void registerExisting() {
        createCourse("既存", 10000, 10, 0, session(1, "2026-11-01T10:00", "A教室", "山田"));
    }

    @Test
    void 要件8_4_登録済みの回と教室が重なるならR08を返し何も保存しない() throws Exception {
        registerExisting();

        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "A教室", "佐藤")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R08"))
                .andExpect(jsonPath("$.message").value("教室が重なります"));

        assertOnlyExistingSaved();
    }

    @Test
    void 要件8_5_登録済みの回と講師が重なるならR09を返し何も保存しない() throws Exception {
        registerExisting();

        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "B教室", "山田")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R09"))
                .andExpect(jsonPath("$.message").value("講師が重なります"));

        assertOnlyExistingSaved();
    }

    @Test
    void 要件8_4_教室も講師も違えば時間帯が重なっても登録できる() throws Exception {
        registerExisting();

        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "B教室", "佐藤")))
                .andExpect(status().isCreated());
    }

    @Test
    void 要件8_1_教室と講師の両方が重なるときは教室が先でR08を返す() throws Exception {
        registerExisting();

        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R08"));

        assertOnlyExistingSaved();
    }

    @Test
    void 要件8_1_教室の検査はすべての回に対して講師の検査より先に行う() throws Exception {
        registerExisting();

        // 1 回目は講師だけが重なり、2 回目は教室だけが重なる。教室の検査が先なので R08
        register(courseJson("SERIES", 10,
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "B教室", "山田"),
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "A教室", "佐藤")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R08"));

        assertOnlyExistingSaved();
    }

    @Test
    void 要件8_1_開始と終了の誤りは教室の重なりより先でR07を返す() throws Exception {
        registerExisting();

        register(courseJson("SERIES", 10,
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "A教室", "山田"),
                sessionJson("2026-11-08T12:00:00+09:00", "2026-11-08T10:00:00+09:00", "C教室", "鈴木")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"));

        assertOnlyExistingSaved();
    }

    @Test
    void 要件8_1_定員の誤りは教室と講師の重なりより先でR07を返す() throws Exception {
        registerExisting();

        register(courseJson("SINGLE", 0,
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R07"));

        assertOnlyExistingSaved();
    }

    @Test
    void 要件8_4_同じ登録内の他の回と教室が重なるならR08を返し何も保存しない() throws Exception {
        register(courseJson("SERIES", 10,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田"),
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "A教室", "佐藤")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R08"));

        assertNothingSaved();
    }

    @Test
    void 要件8_5_同じ登録内の他の回と講師が重なるならR09を返し何も保存しない() throws Exception {
        register(courseJson("SERIES", 10,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田"),
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "B教室", "山田")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R09"));

        assertNothingSaved();
    }

    @Test
    void 要件8_4_8_5_同じ登録内で教室も講師も違う回は時間帯が重なっても登録できる() throws Exception {
        register(courseJson("SERIES", 10,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田"),
                sessionJson("2026-11-01T11:00:00+09:00", "2026-11-01T13:00:00+09:00", "B教室", "佐藤")))
                .andExpect(status().isCreated());

        assertThat(courseSessionRepository.count()).isEqualTo(2);
    }

    @Test
    void 要件8_6_登録済みの回の終了と新しい回の開始が同じなら教室も講師も重ならず登録できる() throws Exception {
        registerExisting();

        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T12:00:00+09:00", "2026-11-01T14:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isCreated());

        assertThat(courseRepository.count()).isEqualTo(2);
        assertThat(courseSessionRepository.count()).isEqualTo(2);
    }

    @Test
    void 要件8_6_新しい回の終了と登録済みの回の開始が同じなら重ならず登録できる() throws Exception {
        registerExisting();

        register(courseJson("SINGLE", 10,
                sessionJson("2026-11-01T08:00:00+09:00", "2026-11-01T10:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isCreated());
    }

    @Test
    void 要件8_6_同じ登録内で終了と開始が同じ回は教室も講師も重ならず登録できる() throws Exception {
        register(courseJson("SERIES", 10,
                sessionJson("2026-11-01T10:00:00+09:00", "2026-11-01T12:00:00+09:00", "A教室", "山田"),
                sessionJson("2026-11-01T12:00:00+09:00", "2026-11-01T14:00:00+09:00", "A教室", "山田")))
                .andExpect(status().isCreated());

        assertThat(courseSessionRepository.count()).isEqualTo(2);
    }
}
