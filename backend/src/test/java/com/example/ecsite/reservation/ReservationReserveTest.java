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

/** 要件 3.1 から 3.5、4.1、4.2、4.4 - 予約の受付と席の確保 */
@AutoConfigureMockMvc
class ReservationReserveTest extends ReservationTestBase {
    long customer;
    long otherCustomer;

    @Autowired MockMvc mvc;

    @BeforeEach
    void setUpCustomers() {
        customer = data.createCustomer("a@example.com");
        otherCustomer = data.createCustomer("b@example.com");
    }

    private ResultActions reserve(long customerId, Course course) throws Exception {
        return mvc.perform(post("/api/reservation/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":" + customerId + ",\"courseId\":" + course.getId() + "}"));
    }

    private long activeCount(Course course) {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getCourse().getId().equals(course.getId()) && r.getStatus() == ReservationStatus.ACTIVE)
                .count();
    }

    private long totalCount() {
        return reservationRepository.count();
    }

    @Test
    void 要件4_1_受け付けると201で予約を返し残席が1減り有効な予約が1件増える() throws Exception {
        Course course = createCourse("入門書道", 3000, 10, 4, session(1, "2026-11-01T10:00", "A教室", "山田"));

        reserve(customer, course)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.courseId").value(course.getId()))
                .andExpect(jsonPath("$.courseName").value("入門書道"))
                .andExpect(jsonPath("$.price").value(3000))
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].startsAt").value("2026-11-01T10:00:00+09:00"))
                .andExpect(jsonPath("$.sessions[0].room").value("A教室"))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.cancelFee").value(0));

        assertThat(reload(course).getReservedCount()).isEqualTo(5);
        assertThat(reload(course).remainingSeats()).isEqualTo(5);
        assertThat(activeCount(course)).isEqualTo(1);
        assertThat(reservationRepository.findAll().get(0).getCustomerId()).isEqualTo(customer);
    }

    @Test
    void 要件4_1_開始日の当日ならキャンセル料は受講料の半額() throws Exception {
        Course course = createCourse("当日", 3001, 10, 0, session(1, "2026-10-21T15:00", "A教室", "山田"));

        reserve(customer, course)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.cancelFee").value(1500));
    }

    @Test
    void 要件4_2_連続講座は1件の予約で1席を確保する() throws Exception {
        Course course = createCourse("連続講座", 12000, 10, 0,
                session(1, "2026-11-01T10:00", "A教室", "山田"),
                session(2, "2026-11-08T10:00", "A教室", "山田"),
                session(3, "2026-11-15T10:00", "A教室", "山田"));

        reserve(customer, course)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessions.length()").value(3))
                .andExpect(jsonPath("$.price").value(12000));

        assertThat(reload(course).getReservedCount()).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void 要件3_2_残席が0ならR01で予約を登録しない() throws Exception {
        Course course = createCourse("満席", 3000, 2, 2, session(1, "2026-11-01T10:00", "A教室", "山田"));

        reserve(customer, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R01"))
                .andExpect(jsonPath("$.message").value("満席です"));

        assertThat(reload(course).getReservedCount()).isEqualTo(2);
        assertThat(totalCount()).isZero();
    }

    @Test
    void 要件3_3_3_4_締め切りの1時間前ちょうどは受け付け1秒後はR02() throws Exception {
        Course course = createCourse("直前", 3000, 10, 0, session(1, "2026-10-21T12:00", "A教室", "山田"));
        Course other = createCourse("直前2", 3000, 10, 0, session(1, "2026-10-21T12:00", "B教室", "佐藤"));

        clock.set(at("2026-10-21T11:00"));
        reserve(customer, course).andExpect(status().isCreated());

        clock.set(at("2026-10-21T11:00").plusSeconds(1));
        reserve(otherCustomer, other)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R02"))
                .andExpect(jsonPath("$.message").value("受付を締め切りました"));

        assertThat(reload(other).getReservedCount()).isZero();
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void 要件3_5_同じ講座の有効な予約があればR03で件数と席を変えない() throws Exception {
        Course course = createCourse("二重", 3000, 10, 1, session(1, "2026-11-01T10:00", "A教室", "山田"));
        createReservation(customer, course, ReservationStatus.ACTIVE);

        reserve(customer, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R03"))
                .andExpect(jsonPath("$.message").value("すでに予約しています"));

        assertThat(reload(course).getReservedCount()).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void 要件3_5_ほかのお客様の予約はR03にならない() throws Exception {
        Course course = createCourse("別の人", 3000, 10, 1, session(1, "2026-11-01T10:00", "A教室", "山田"));
        createReservation(otherCustomer, course, ReservationStatus.ACTIVE);

        reserve(customer, course).andExpect(status().isCreated());

        assertThat(reload(course).getReservedCount()).isEqualTo(2);
        assertThat(activeCount(course)).isEqualTo(2);
    }

    @Test
    void 要件3_1_残席0と締め切り後が同時ならR01() throws Exception {
        Course course = createCourse("満席かつ締め切り", 3000, 1, 1, session(1, "2026-10-21T10:30", "A教室", "山田"));

        reserve(customer, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R01"));

        assertThat(reload(course).getReservedCount()).isEqualTo(1);
        assertThat(totalCount()).isZero();
    }

    @Test
    void 要件3_1_締め切り後と二重予約が同時ならR02() throws Exception {
        Course course = createCourse("締め切りかつ二重", 3000, 10, 1, session(1, "2026-10-21T10:30", "A教室", "山田"));
        createReservation(customer, course, ReservationStatus.ACTIVE);

        reserve(customer, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R02"));

        assertThat(reload(course).getReservedCount()).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }

    @Test
    void 要件3_1_残席0と二重予約が同時ならR01() throws Exception {
        Course course = createCourse("満席かつ二重", 3000, 1, 1, session(1, "2026-11-01T10:00", "A教室", "山田"));
        createReservation(customer, course, ReservationStatus.ACTIVE);

        reserve(customer, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R01"));

        assertThat(reload(course).getReservedCount()).isEqualTo(1);
        assertThat(totalCount()).isEqualTo(1);
    }
}
