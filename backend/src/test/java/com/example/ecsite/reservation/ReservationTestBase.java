package com.example.ecsite.reservation;

import com.example.ecsite.MutableClock;
import com.example.ecsite.TestClockConfig;
import com.example.ecsite.TestDataHelper;
import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.CourseKind;
import com.example.ecsite.reservation.entity.CourseSession;
import com.example.ecsite.reservation.entity.Reservation;
import com.example.ecsite.reservation.entity.ReservationStatus;
import com.example.ecsite.reservation.repository.CourseRepository;
import com.example.ecsite.reservation.repository.CourseSessionRepository;
import com.example.ecsite.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

/** 予約のテストの共通の基盤。現在日時を固定し、テストごとにデータを空に戻して、講座・回・予約・お客様を作る。 */
@SpringBootTest
@Import(TestClockConfig.class)
abstract class ReservationTestBase {
    /** 固定した現在日時（日本時間 2026-10-21 10:00）。 */
    static final OffsetDateTime NOW = at("2026-10-21T10:00");

    @Autowired TestDataHelper data;
    @Autowired MutableClock clock;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseSessionRepository courseSessionRepository;
    @Autowired ReservationRepository reservationRepository;

    @Autowired TransactionTemplate transaction;

    @BeforeEach
    void setUpBase() {
        data.reset();
        clock.set(NOW);
    }

    /** 日本時間の日時（例 "2026-11-01T10:00"）。 */
    static OffsetDateTime at(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + ":00+09:00");
    }

    /** 回の入力。講座を作るときに、講座へ結び付けて保存する。 */
    record SessionSpec(int no, OffsetDateTime startsAt, OffsetDateTime endsAt, String room, String instructor) {}

    /** 2 時間の回。 */
    static SessionSpec session(int no, String startsAt, String room, String instructor) {
        OffsetDateTime start = at(startsAt);
        return new SessionSpec(no, start, start.plusHours(2), room, instructor);
    }

    /**
     * 講座と回を保存して返す。reserved_count は更新できない列のため、保存するときに設定する。
     * 回が 1 件なら単発、2 件以上なら連続とする。
     */
    Course createCourse(String name, long price, int capacity, int reservedCount, SessionSpec... sessions) {
        Course course = new Course();
        course.setName(name);
        course.setCategory("書道");
        course.setKind(sessions.length > 1 ? CourseKind.SERIES : CourseKind.SINGLE);
        course.setPrice(price);
        course.setCapacity(capacity);
        course.setReservedCount(reservedCount);
        for (SessionSpec spec : sessions) {
            CourseSession session = new CourseSession();
            session.setCourse(course);
            session.setSessionNo(spec.no());
            session.setStartsAt(spec.startsAt());
            session.setEndsAt(spec.endsAt());
            session.setRoom(spec.room());
            session.setInstructor(spec.instructor());
            course.getSessions().add(session);
        }
        return courseRepository.saveAndFlush(course);
    }

    /** 予約を保存して返す。CANCELLED のときはキャンセル日時と料金 0 を付ける。 */
    Reservation createReservation(long customerId, Course course, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setCustomerId(customerId);
        reservation.setCourse(course);
        reservation.setStatus(status);
        reservation.setReservedAt(NOW);
        if (status == ReservationStatus.CANCELLED) {
            reservation.setCancelledAt(NOW);
            reservation.setCancelFee(0L);
        }
        return reservationRepository.saveAndFlush(reservation);
    }

    /** flush を伴う条件付き更新は、本番では service のトランザクションの中で呼ぶ。テストでは 1 回分のトランザクションで包む。 */
    <T> T tx(Supplier<T> action) {
        return transaction.execute(status -> action.get());
    }

    /** 条件付き更新のあとに、データベースの値を読み直す。 */
    Course reload(Course course) {
        return courseRepository.findWithSessionsById(course.getId()).orElseThrow();
    }
}
