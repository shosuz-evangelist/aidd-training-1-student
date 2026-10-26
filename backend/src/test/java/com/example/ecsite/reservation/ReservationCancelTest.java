package com.example.ecsite.reservation;

import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.Reservation;
import com.example.ecsite.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 5.1 から 5.5 - 予約一覧（本人の有効な予約、現在のキャンセル料、キャンセルの可否）、要件 6.1 から 6.7 - キャンセル */
@AutoConfigureMockMvc
class ReservationCancelTest extends ReservationTestBase {
    long customer;

    @Autowired MockMvc mvc;

    @BeforeEach
    void setUpCustomer() {
        customer = data.createCustomer("a@example.com");
    }

    private ResultActions list(long customerId) throws Exception {
        return mvc.perform(get("/api/reservation/reservations").param("customerId", String.valueOf(customerId)));
    }

    @Test
    void 要件5_1_本人の有効な予約だけが講座名と日程と受講料とともに出る() throws Exception {
        long other = data.createCustomer("b@example.com");
        Course mine = createCourse("自分の講座", 3000, 10, 1, session(1, "2026-11-01T10:00", "A教室", "山田"));
        Course cancelled = createCourse("キャンセル済み", 4000, 10, 0, session(1, "2026-11-02T10:00", "A教室", "山田"));
        Course others = createCourse("他人の講座", 5000, 10, 1, session(1, "2026-11-03T10:00", "A教室", "山田"));
        long myId = createReservation(customer, mine, ReservationStatus.ACTIVE).getId();
        createReservation(customer, cancelled, ReservationStatus.CANCELLED);
        createReservation(other, others, ReservationStatus.ACTIVE);

        list(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(myId))
                .andExpect(jsonPath("$[0].courseId").value(mine.getId()))
                .andExpect(jsonPath("$[0].courseName").value("自分の講座"))
                .andExpect(jsonPath("$[0].price").value(3000))
                .andExpect(jsonPath("$[0].sessions.length()").value(1))
                .andExpect(jsonPath("$[0].sessions[0].startsAt").value("2026-11-01T10:00:00+09:00"))
                .andExpect(jsonPath("$[0].sessions[0].room").value("A教室"))
                .andExpect(jsonPath("$[0].sessions[0].instructor").value("山田"));
    }

    @Test
    void 要件5_1_有効な予約がなければ空の一覧を返す() throws Exception {
        list(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 要件5_3_第1回の開始日の前日までならキャンセル料は0円でキャンセルできる() throws Exception {
        // 現在は 2026-10-21 10:00。開始は翌日 2026-10-22
        Course course = createCourse("前日", 3001, 10, 1, session(1, "2026-10-22T09:00", "A教室", "山田"));
        createReservation(customer, course, ReservationStatus.ACTIVE);

        list(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cancelFee").value(0))
                .andExpect(jsonPath("$[0].cancellable").value(true));
    }

    @Test
    void 要件5_4_開始日の当日で開始前ならキャンセル料は受講料の50パーセントの切り捨て() throws Exception {
        // 現在は 2026-10-21 10:00。開始は同日 15:00。3001 円の 50 % は 1500.5 円で、切り捨てて 1500 円
        Course course = createCourse("当日", 3001, 10, 1, session(1, "2026-10-21T15:00", "A教室", "山田"));
        createReservation(customer, course, ReservationStatus.ACTIVE);

        list(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cancelFee").value(1500))
                .andExpect(jsonPath("$[0].cancellable").value(true));
    }

    @Test
    void 要件5_5_開始時刻以後はキャンセルできずキャンセル料は0円() throws Exception {
        // 現在は 2026-10-21 10:00。開始 09:00 は過ぎている
        Course started = createCourse("開始後", 3000, 10, 1, session(1, "2026-10-21T09:00", "A教室", "山田"));
        // 開始時刻ちょうども開始後として扱う
        Course justStarted = createCourse("ちょうど", 4000, 10, 1, session(1, "2026-10-21T10:00", "B教室", "佐藤"));
        createReservation(customer, started, ReservationStatus.ACTIVE);
        createReservation(customer, justStarted, ReservationStatus.ACTIVE);

        list(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cancelFee").value(0))
                .andExpect(jsonPath("$[0].cancellable").value(false))
                .andExpect(jsonPath("$[1].cancelFee").value(0))
                .andExpect(jsonPath("$[1].cancellable").value(false));
    }

    @Test
    void 要件6_7_連続講座のキャンセル料は第1回の開始日を基準に受講料で算出する() throws Exception {
        Course series = createCourse("連続", 9000, 10, 1,
                session(1, "2026-10-21T15:00", "A教室", "山田"),
                session(2, "2026-10-28T15:00", "A教室", "山田"));
        createReservation(customer, series, ReservationStatus.ACTIVE);

        list(customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessions.length()").value(2))
                .andExpect(jsonPath("$[0].cancelFee").value(4500))
                .andExpect(jsonPath("$[0].cancellable").value(true));
    }

    private ResultActions cancel(long reservationId, long customerId) throws Exception {
        return mvc.perform(post("/api/reservation/reservations/{id}/cancel", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":" + customerId + "}"));
    }

    @Test
    void 要件6_1_キャンセルすると予約がキャンセル済みになり残席が1戻る() throws Exception {
        Course course = createCourse("当日", 3001, 10, 1, session(1, "2026-10-21T15:00", "A教室", "山田"));
        Reservation reservation = createReservation(customer, course, ReservationStatus.ACTIVE);

        cancel(reservation.getId(), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservation.getId()))
                .andExpect(jsonPath("$.cancelFee").value(1500));

        Reservation saved = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(saved.getCancelFee()).isEqualTo(1500L);
        assertThat(saved.getCancelledAt()).isNotNull();
        assertThat(reload(course).getReservedCount()).isEqualTo(0);
    }

    @Test
    void 要件6_2_開始日の前日までのキャンセルは料金0円で保存する() throws Exception {
        Course course = createCourse("前日", 3001, 10, 1, session(1, "2026-10-22T09:00", "A教室", "山田"));
        Reservation reservation = createReservation(customer, course, ReservationStatus.ACTIVE);

        cancel(reservation.getId(), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelFee").value(0));

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getCancelFee()).isEqualTo(0L);
    }

    @Test
    void 要件6_5_開始後のキャンセルはR05で409を返し何も変えない() throws Exception {
        Course course = createCourse("開始後", 3000, 10, 1, session(1, "2026-10-21T09:00", "A教室", "山田"));
        Reservation reservation = createReservation(customer, course, ReservationStatus.ACTIVE);

        cancel(reservation.getId(), customer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R05"));

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reload(course).getReservedCount()).isEqualTo(1);
    }

    @Test
    void 要件6_6_本人以外のキャンセルはR06で404を返し何も変えない() throws Exception {
        long other = data.createCustomer("b@example.com");
        Course course = createCourse("他人", 3000, 10, 1, session(1, "2026-10-21T15:00", "A教室", "山田"));
        Reservation reservation = createReservation(customer, course, ReservationStatus.ACTIVE);

        cancel(reservation.getId(), other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R06"));

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reload(course).getReservedCount()).isEqualTo(1);
    }

    @Test
    void 要件6_6_他人の開始後の予約はR05ではなくR06を返す() throws Exception {
        long other = data.createCustomer("b@example.com");
        Course course = createCourse("開始後", 3000, 10, 1, session(1, "2026-10-21T09:00", "A教室", "山田"));
        Reservation reservation = createReservation(customer, course, ReservationStatus.ACTIVE);

        cancel(reservation.getId(), other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R06"));
    }

    @Test
    void 要件6_6_存在しない予約はR06で404を返す() throws Exception {
        cancel(999999L, customer)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("R06"));
    }

    @Test
    void 要件6_7_連続講座のキャンセル料は全回分の受講料を基準に1件分で残席は1戻る() throws Exception {
        Course series = createCourse("連続", 9000, 10, 1,
                session(1, "2026-10-21T15:00", "A教室", "山田"),
                session(2, "2026-10-28T15:00", "A教室", "山田"));
        Reservation reservation = createReservation(customer, series, ReservationStatus.ACTIVE);

        cancel(reservation.getId(), customer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelFee").value(4500));

        assertThat(reload(series).getReservedCount()).isEqualTo(0);
    }
}
