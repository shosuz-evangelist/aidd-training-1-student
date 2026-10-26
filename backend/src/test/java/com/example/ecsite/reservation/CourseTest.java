package com.example.ecsite.reservation;

import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.CourseKind;
import com.example.ecsite.reservation.entity.CourseSession;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Course の受付可否の判定（要件 2.4, 2.5）の単体テスト。 */
class CourseTest {
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-11-01T10:00:00+09:00");

    private Course course(int capacity, int reservedCount) {
        Course course = new Course();
        course.setName("書道入門");
        course.setCategory("書道");
        course.setKind(CourseKind.SINGLE);
        course.setPrice(3000);
        course.setCapacity(capacity);
        course.setReservedCount(reservedCount);
        course.getSessions().add(session(course, 1, START));
        return course;
    }

    private CourseSession session(Course course, int no, OffsetDateTime startsAt) {
        CourseSession session = new CourseSession();
        session.setCourse(course);
        session.setSessionNo(no);
        session.setStartsAt(startsAt);
        session.setEndsAt(startsAt.plusHours(2));
        session.setRoom("A");
        session.setInstructor("山田");
        return session;
    }

    @Test
    void openExactlyOneHourBeforeFirstSession() {
        assertThat(course(10, 0).isOpenFor(START.minusHours(1))).isTrue();
    }

    @Test
    void closedOneSecondAfterDeadline() {
        assertThat(course(10, 0).isOpenFor(START.minusHours(1).plusSeconds(1))).isFalse();
    }

    @Test
    void closedWhenNoSeatsLeft() {
        assertThat(course(10, 10).isOpenFor(START.minusDays(1))).isFalse();
    }

    @Test
    void openWhenOneSeatLeft() {
        assertThat(course(10, 9).isOpenFor(START.minusDays(1))).isTrue();
    }

    @Test
    void usesFirstSessionNotLaterOnes() {
        Course course = course(10, 0);
        // 第 2 回を先に並べても、判定は session_no = 1 の回で行う
        course.getSessions().add(0, session(course, 2, START.plusDays(7)));
        assertThat(course.isOpenFor(START.minusMinutes(30))).isFalse();
        assertThat(course.isOpenFor(START.minusHours(2))).isTrue();
    }
}
