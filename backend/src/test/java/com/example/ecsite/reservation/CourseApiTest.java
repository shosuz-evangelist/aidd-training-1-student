package com.example.ecsite.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 2 - 講座一覧の表示 */
@AutoConfigureMockMvc
class CourseApiTest extends ReservationTestBase {
    @Autowired MockMvc mvc;

    @Test
    void 要件2_1_講座の項目と全回の日程を返す() throws Exception {
        var course = createCourse("入門書道", 12000, 10, 3,
                session(1, "2026-11-01T10:00", "A教室", "山田"),
                session(2, "2026-11-08T10:00", "B教室", "佐藤"));

        mvc.perform(get("/api/reservation/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(course.getId()))
                .andExpect(jsonPath("$[0].name").value("入門書道"))
                .andExpect(jsonPath("$[0].category").value("書道"))
                .andExpect(jsonPath("$[0].kind").value("SERIES"))
                .andExpect(jsonPath("$[0].price").value(12000))
                .andExpect(jsonPath("$[0].remaining").value(7))
                .andExpect(jsonPath("$[0].sessions.length()").value(2))
                .andExpect(jsonPath("$[0].sessions[0].startsAt").value("2026-11-01T10:00:00+09:00"))
                .andExpect(jsonPath("$[0].sessions[0].endsAt").value("2026-11-01T12:00:00+09:00"))
                .andExpect(jsonPath("$[0].sessions[0].room").value("A教室"))
                .andExpect(jsonPath("$[0].sessions[0].instructor").value("山田"))
                .andExpect(jsonPath("$[0].sessions[1].startsAt").value("2026-11-08T10:00:00+09:00"))
                .andExpect(jsonPath("$[0].sessions[1].room").value("B教室"));
    }

    @Test
    void 要件2_1_単発の講座の種類は単発() throws Exception {
        createCourse("単発講座", 3000, 5, 0, session(1, "2026-11-01T10:00", "A教室", "山田"));

        mvc.perform(get("/api/reservation/courses"))
                .andExpect(jsonPath("$[0].kind").value("SINGLE"))
                .andExpect(jsonPath("$[0].sessions.length()").value(1));
    }

    @Test
    void 要件2_2_残席が1以上なら残りn席と表示し予約できる() throws Exception {
        createCourse("残席あり", 3000, 10, 7, session(1, "2026-11-01T10:00", "A教室", "山田"));

        mvc.perform(get("/api/reservation/courses"))
                .andExpect(jsonPath("$[0].remaining").value(3))
                .andExpect(jsonPath("$[0].seatLabel").value("残り 3 席"))
                .andExpect(jsonPath("$[0].reservable").value(true));
    }

    @Test
    void 要件2_2_残席が1でも残り1席と表示する() throws Exception {
        createCourse("残席1", 3000, 10, 9, session(1, "2026-11-01T10:00", "A教室", "山田"));

        mvc.perform(get("/api/reservation/courses"))
                .andExpect(jsonPath("$[0].seatLabel").value("残り 1 席"))
                .andExpect(jsonPath("$[0].reservable").value(true));
    }

    @Test
    void 要件2_3_2_4_残席が0なら満席と表示し予約できない() throws Exception {
        createCourse("満席", 3000, 10, 10, session(1, "2026-11-01T10:00", "A教室", "山田"));

        mvc.perform(get("/api/reservation/courses"))
                .andExpect(jsonPath("$[0].remaining").value(0))
                .andExpect(jsonPath("$[0].seatLabel").value("満席"))
                .andExpect(jsonPath("$[0].reservable").value(false));
    }

    @Test
    void 要件2_5_第1回の1時間前を過ぎたら予約できない() throws Exception {
        createCourse("直前", 3000, 10, 0, session(1, "2026-10-21T12:00", "A教室", "山田"));

        // 現在 10:00。第 1 回の 1 時間前は 11:00 なので、10:59:59 までは予約できる。
        clock.set(at("2026-10-21T11:00"));
        mvc.perform(get("/api/reservation/courses"))
                .andExpect(jsonPath("$[0].reservable").value(true));

        clock.set(at("2026-10-21T11:00").plusSeconds(1));
        mvc.perform(get("/api/reservation/courses"))
                .andExpect(jsonPath("$[0].reservable").value(false))
                .andExpect(jsonPath("$[0].seatLabel").value("残り 10 席"));
    }

    @Test
    void 要件2_1_一覧は第1回の開始日時の順に並ぶ() throws Exception {
        createCourse("三番目", 1000, 5, 0, session(1, "2026-12-01T10:00", "A教室", "山田"));
        createCourse("一番目", 1000, 5, 0,
                session(1, "2026-11-01T10:00", "A教室", "山田"),
                session(2, "2026-12-20T10:00", "A教室", "山田"));
        createCourse("二番目", 1000, 5, 0, session(1, "2026-11-15T10:00", "B教室", "佐藤"));

        mvc.perform(get("/api/reservation/courses"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("一番目"))
                .andExpect(jsonPath("$[1].name").value("二番目"))
                .andExpect(jsonPath("$[2].name").value("三番目"));
    }
}
