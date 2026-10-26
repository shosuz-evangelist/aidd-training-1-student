package com.example.ecsite.reservation;

import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 3.1、3.6 から 3.9、4.5 - 予約の受付の時間帯の重なりの検査（連続講座は全回で比べる） */
@AutoConfigureMockMvc
class ReservationOverlapTest extends ReservationTestBase {
    long customer;

    @Autowired MockMvc mvc;

    @BeforeEach
    void setUpCustomer() {
        customer = data.createCustomer("a@example.com");
    }

    private ResultActions reserve(Course course) throws Exception {
        return mvc.perform(post("/api/reservation/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":" + customer + ",\"courseId\":" + course.getId() + "}"));
    }

    @Test
    void 要件3_6_第1回が重なる講座はR04で件数と席を変えない() throws Exception {
        Course booked = createCourse("既存", 3000, 10, 1, session(1, "2026-11-01T10:00", "A教室", "山田"));
        Course target = createCourse("重なる", 3000, 10, 0, session(1, "2026-11-01T11:00", "B教室", "佐藤"));
        createReservation(customer, booked, ReservationStatus.ACTIVE);

        reserve(target)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R04"));

        assertThat(reload(target).getReservedCount()).isZero();
        assertThat(reload(booked).getReservedCount()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void 要件3_7_接するだけなら予約できる() throws Exception {
        Course booked = createCourse("既存", 3000, 10, 1, session(1, "2026-11-01T10:00", "A教室", "山田"));
        Course after = createCourse("直後", 3000, 10, 0, session(1, "2026-11-01T12:00", "A教室", "山田"));
        Course before = createCourse("直前", 3000, 10, 0, session(1, "2026-11-01T08:00", "A教室", "山田"));
        createReservation(customer, booked, ReservationStatus.ACTIVE);

        reserve(after).andExpect(status().isCreated());
        reserve(before).andExpect(status().isCreated());

        assertThat(reload(after).getReservedCount()).isEqualTo(1);
        assertThat(reload(before).getReservedCount()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(3);
    }

    @Test
    void 要件3_7_連続講座は全回で比べる_予約しようとする講座の後の回が重なればR04() throws Exception {
        Course booked = createCourse("既存", 3000, 10, 1, session(1, "2026-11-08T10:00", "A教室", "山田"));
        Course series = createCourse("連続", 9000, 10, 0,
                session(1, "2026-11-01T10:00", "B教室", "佐藤"),
                session(2, "2026-11-08T11:00", "B教室", "佐藤"));
        createReservation(customer, booked, ReservationStatus.ACTIVE);

        reserve(series)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R04"));

        assertThat(reload(series).getReservedCount()).isZero();
    }

    @Test
    void 要件3_7_連続講座は全回で比べる_予約済みの講座の後の回が重なればR04() throws Exception {
        Course bookedSeries = createCourse("既存の連続", 9000, 10, 1,
                session(1, "2026-11-01T10:00", "A教室", "山田"),
                session(2, "2026-11-08T10:00", "A教室", "山田"));
        Course target = createCourse("単発", 3000, 10, 0, session(1, "2026-11-08T10:00", "B教室", "佐藤"));
        createReservation(customer, bookedSeries, ReservationStatus.ACTIVE);

        reserve(target)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R04"));

        assertThat(reload(target).getReservedCount()).isZero();
    }

    @Test
    void 要件3_7_連続講座の第1回どうしが重なればR04() throws Exception {
        Course bookedSeries = createCourse("既存の連続", 9000, 10, 1,
                session(1, "2026-11-01T10:00", "A教室", "山田"),
                session(2, "2026-11-08T10:00", "A教室", "山田"));
        Course targetSeries = createCourse("重なる連続", 9000, 10, 0,
                session(1, "2026-11-01T11:00", "B教室", "佐藤"),
                session(2, "2026-11-15T11:00", "B教室", "佐藤"));
        createReservation(customer, bookedSeries, ReservationStatus.ACTIVE);

        reserve(targetSeries)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R04"));

        assertThat(reload(targetSeries).getReservedCount()).isZero();
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void 要件3_9_キャンセル済みの予約はR04の判定に含めない() throws Exception {
        Course cancelled = createCourse("キャンセル済み", 3000, 10, 0, session(1, "2026-11-01T10:00", "A教室", "山田"));
        Course target = createCourse("重なる", 3000, 10, 0, session(1, "2026-11-01T11:00", "B教室", "佐藤"));
        createReservation(customer, cancelled, ReservationStatus.CANCELLED);

        reserve(target).andExpect(status().isCreated());

        assertThat(reload(target).getReservedCount()).isEqualTo(1);
    }

    @Test
    void 要件3_9_4_5_キャンセル後の再予約は新しい予約として受け付ける() throws Exception {
        Course course = createCourse("再予約", 3000, 10, 0, session(1, "2026-11-01T10:00", "A教室", "山田"));
        createReservation(customer, course, ReservationStatus.CANCELLED);

        reserve(course)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(course.getId()));

        assertThat(reload(course).getReservedCount()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(2);
        assertThat(reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatus.ACTIVE).count()).isEqualTo(1);
    }

    @Test
    void 要件3_1_同じ講座の二重予約はR04ではなくR03() throws Exception {
        Course course = createCourse("二重", 3000, 10, 1, session(1, "2026-11-01T10:00", "A教室", "山田"));
        createReservation(customer, course, ReservationStatus.ACTIVE);

        reserve(course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R03"));

        assertThat(reload(course).getReservedCount()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }
}
