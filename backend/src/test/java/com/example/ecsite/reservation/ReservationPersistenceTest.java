package com.example.ecsite.reservation;

import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.Reservation;
import com.example.ecsite.reservation.entity.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 永続化層の条件付き更新と重なりの検索（要件 4.3, 9.2）を確かめる。 */
class ReservationPersistenceTest extends ReservationTestBase {

    // --- 席の確保（4.3） ---

    @Test
    void tryAcquireSeatReturnsZeroWhenCapacityReached() {
        Course course = createCourse("書道入門", 3000, 2, 2, session(1, "2026-11-01T10:00", "A", "山田"));

        assertThat(tx(() -> courseRepository.tryAcquireSeat(course.getId()))).isZero();
        assertThat(reload(course).getReservedCount()).isEqualTo(2);
    }

    @Test
    void tryAcquireSeatTakesTheLastSeatOnceOnly() {
        Course course = createCourse("書道入門", 3000, 2, 1, session(1, "2026-11-01T10:00", "A", "山田"));

        assertThat(tx(() -> courseRepository.tryAcquireSeat(course.getId()))).isEqualTo(1);
        assertThat(reload(course).getReservedCount()).isEqualTo(2);
        assertThat(tx(() -> courseRepository.tryAcquireSeat(course.getId()))).isZero();
        assertThat(reload(course).getReservedCount()).isEqualTo(2);
    }

    @Test
    void releaseSeatReturnsZeroWhenNothingReserved() {
        Course course = createCourse("書道入門", 3000, 2, 0, session(1, "2026-11-01T10:00", "A", "山田"));

        assertThat(tx(() -> courseRepository.releaseSeat(course.getId()))).isZero();
        assertThat(reload(course).getReservedCount()).isZero();
    }

    // --- 教室と講師の重なり（8.6 の土台） ---

    @Test
    void roomSearchReturnsOnlyOverlappingSessions() {
        createCourse("重なる", 1000, 5, 0, session(1, "2026-11-01T10:00", "A", "山田"));
        createCourse("接する前", 1000, 5, 0, session(1, "2026-11-01T08:00", "A", "佐藤"));
        createCourse("接する後", 1000, 5, 0, session(1, "2026-11-01T12:00", "A", "鈴木"));
        createCourse("別の教室", 1000, 5, 0, session(1, "2026-11-01T10:00", "B", "高橋"));

        // 検索範囲 11:00 から 12:00。10:00 から 12:00 の回だけが重なる（8:00 から 10:00 と 12:00 から 14:00 は接するだけ）
        var found = courseSessionRepository.findRoomOverlaps("A", at("2026-11-01T11:00"), at("2026-11-01T12:00"));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getInstructor()).isEqualTo("山田");
    }

    @Test
    void roomSearchTreatsEndEqualToStartAsNotOverlapping() {
        createCourse("午前", 1000, 5, 0, session(1, "2026-11-01T10:00", "A", "山田"));

        assertThat(courseSessionRepository.findRoomOverlaps("A", at("2026-11-01T12:00"), at("2026-11-01T14:00"))).isEmpty();
        assertThat(courseSessionRepository.findRoomOverlaps("A", at("2026-11-01T08:00"), at("2026-11-01T10:00"))).isEmpty();
    }

    @Test
    void instructorSearchReturnsOnlyOverlappingSessions() {
        createCourse("重なる", 1000, 5, 0, session(1, "2026-11-01T10:00", "A", "山田"));
        createCourse("接する", 1000, 5, 0, session(1, "2026-11-01T12:00", "B", "山田"));
        createCourse("別の講師", 1000, 5, 0, session(1, "2026-11-01T10:00", "C", "佐藤"));

        var found = courseSessionRepository.findInstructorOverlaps("山田", at("2026-11-01T11:00"), at("2026-11-01T12:00"));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRoom()).isEqualTo("A");
        assertThat(courseSessionRepository.findInstructorOverlaps("山田", at("2026-11-01T12:00"), at("2026-11-01T14:00")))
                .extracting(s -> s.getRoom()).containsExactly("B");
    }

    // --- キャンセルの条件付き更新（6.x の土台） ---

    @Test
    void cancelIfActiveUpdatesActiveReservationOnce() {
        Course course = createCourse("書道入門", 3000, 5, 1, session(1, "2026-11-01T10:00", "A", "山田"));
        long customer = data.createCustomer("a@example.com");
        Reservation reservation = createReservation(customer, course, ReservationStatus.ACTIVE);
        OffsetDateTime cancelledAt = at("2026-10-31T09:00");

        assertThat(tx(() -> reservationRepository.cancelIfActive(reservation.getId(), cancelledAt, 1500L))).isEqualTo(1);

        Reservation cancelled = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.getCancelFee()).isEqualTo(1500L);
        assertThat(cancelled.getCancelledAt().toInstant()).isEqualTo(cancelledAt.toInstant());

        // 2 回目は ACTIVE でないので 0 行で、料金も日時も変わらない
        assertThat(tx(() -> reservationRepository.cancelIfActive(reservation.getId(), at("2026-11-01T09:00"), 0L))).isZero();
        Reservation again = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(again.getCancelFee()).isEqualTo(1500L);
        assertThat(again.getCancelledAt().toInstant()).isEqualTo(cancelledAt.toInstant());
    }

    @Test
    void cancelIfActiveReturnsZeroForNonActiveReservation() {
        Course course = createCourse("書道入門", 3000, 5, 0, session(1, "2026-11-01T10:00", "A", "山田"));
        long customer = data.createCustomer("a@example.com");
        Reservation reservation = createReservation(customer, course, ReservationStatus.CANCELLED);

        assertThat(tx(() -> reservationRepository.cancelIfActive(reservation.getId(), at("2026-10-31T09:00"), 100L))).isZero();
        // 作ったときの料金 0 と日時 NOW のまま、100 円と新しい日時に上書きされない
        Reservation unchanged = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(unchanged.getCancelFee()).isZero();
        assertThat(unchanged.getCancelledAt().toInstant()).isEqualTo(NOW.toInstant());
    }

    // --- 基盤: 固定した現在日時と、テストごとのデータの初期化（9.2） ---

    @Test
    void clockIsFixedAtBaseNow() {
        assertThat(clock.instant()).isEqualTo(NOW.toInstant());
        assertThat(clock.instant()).isNotEqualTo(Instant.now());
        assertThat(clock.getZone().getId()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void dataIsEmptyAtStartOfFirstTest() {
        assertDatabaseEmptyThenCreateData();
    }

    @Test
    void dataIsEmptyAtStartOfSecondTest() {
        assertDatabaseEmptyThenCreateData();
    }

    /** 2 つのテストが同じ検査をする。どちらが後に動いても、前のテストのデータが残っていれば失敗する。 */
    private void assertDatabaseEmptyThenCreateData() {
        assertThat(courseRepository.count()).isZero();
        assertThat(courseSessionRepository.count()).isZero();
        assertThat(reservationRepository.count()).isZero();

        Course course = createCourse("残り物の確認", 1000, 5, 0, session(1, "2026-11-01T10:00", "A", "山田"));
        createReservation(data.createCustomer("reset@example.com"), course, ReservationStatus.ACTIVE);

        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }
}
