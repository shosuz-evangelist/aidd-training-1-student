package com.example.ecsite.reservation.service;

import com.example.ecsite.reservation.dto.CourseRequest;
import com.example.ecsite.reservation.dto.CourseView;
import com.example.ecsite.reservation.exception.ReservationError;
import com.example.ecsite.reservation.exception.ReservationException;
import com.example.ecsite.reservation.dto.SessionView;
import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.CourseSession;
import com.example.ecsite.reservation.repository.CourseRepository;
import com.example.ecsite.reservation.repository.CourseSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseSessionRepository courseSessionRepository;
    private final Clock clock;

    public CourseService(CourseRepository courseRepository, CourseSessionRepository courseSessionRepository,
                         Clock clock) {
        this.courseRepository = courseRepository;
        this.courseSessionRepository = courseSessionRepository;
        this.clock = clock;
    }

    /** 講座の一覧。第 1 回の開始日時の順に並べる（2.1 から 2.5）。 */
    @Transactional(readOnly = true)
    public List<CourseView> list() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return courseRepository.findAllWithSessions().stream()
                .sorted(Comparator.comparing(Course::firstSessionStart).thenComparing(Course::getId))
                .map(course -> toView(course, now))
                .toList();
    }

    /**
     * 講座と回を登録する。8.1 の順に検査し、最初の失敗を返す。1 開始が終了より前、2 定員が 1 以上、3 教室の重なり（R08）、4 講師の重なり（R09）。
     * 重なりは登録済みの回と、同じ登録内の他の回の両方と比べる（終了と開始が同じなら重ならない）。
     * 保存は 1 つのトランザクションで、エラーなら何も残らない（8.7）。回の番号は入力の順に 1 から振る。
     */
    @Transactional
    public CourseView register(CourseRequest request) {
        List<SessionView> inputs = request.sessions() == null ? List.of() : request.sessions();
        for (SessionView input : inputs) {
            if (!input.startsAt().isBefore(input.endsAt())) {
                throw new ReservationException(ReservationError.R07);
            }
        }
        if (request.capacity() < 1) {
            throw new ReservationException(ReservationError.R07);
        }
        // 3 教室の重なり。すべての回を調べてから、4 講師の重なりを調べる
        for (int i = 0; i < inputs.size(); i++) {
            SessionView input = inputs.get(i);
            if (!courseSessionRepository.findRoomOverlaps(input.room(), input.startsAt(), input.endsAt()).isEmpty()
                    || overlapsOtherInput(inputs, i, true)) {
                throw new ReservationException(ReservationError.R08);
            }
        }
        // 4 講師の重なり
        for (int i = 0; i < inputs.size(); i++) {
            SessionView input = inputs.get(i);
            if (!courseSessionRepository.findInstructorOverlaps(input.instructor(), input.startsAt(), input.endsAt()).isEmpty()
                    || overlapsOtherInput(inputs, i, false)) {
                throw new ReservationException(ReservationError.R09);
            }
        }

        Course course = new Course();
        course.setName(request.name());
        course.setCategory(request.category());
        course.setKind(request.kind());
        course.setPrice(request.price());
        course.setCapacity(request.capacity());
        int no = 1;
        for (SessionView input : inputs) {
            CourseSession session = new CourseSession();
            session.setCourse(course);
            session.setSessionNo(no++);
            session.setStartsAt(input.startsAt());
            session.setEndsAt(input.endsAt());
            session.setRoom(input.room());
            session.setInstructor(input.instructor());
            course.getSessions().add(session);
        }
        Course saved = courseRepository.saveAndFlush(course);
        return toView(saved, OffsetDateTime.now(clock));
    }

    /** 同じ登録内の他の回のうち、同じ教室（byRoom）または同じ講師で、時間帯が重なるものがあるか。 */
    private boolean overlapsOtherInput(List<SessionView> inputs, int index, boolean byRoom) {
        SessionView self = inputs.get(index);
        TimeRange range = new TimeRange(self.startsAt(), self.endsAt());
        for (int j = 0; j < inputs.size(); j++) {
            if (j == index) {
                continue;
            }
            SessionView other = inputs.get(j);
            boolean same = byRoom
                    ? Objects.equals(self.room(), other.room())
                    : Objects.equals(self.instructor(), other.instructor());
            if (same && range.overlaps(new TimeRange(other.startsAt(), other.endsAt()))) {
                return true;
            }
        }
        return false;
    }

    private CourseView toView(Course course, OffsetDateTime now) {
        int remaining = course.remainingSeats();
        List<SessionView> sessions = course.getSessions().stream()
                .sorted(Comparator.comparingInt(CourseSession::getSessionNo))
                .map(s -> new SessionView(s.getStartsAt(), s.getEndsAt(), s.getRoom(), s.getInstructor()))
                .toList();
        return new CourseView(course.getId(), course.getName(), course.getCategory(), course.getKind(), sessions,
                course.getPrice(), remaining, seatLabel(remaining), course.isOpenFor(now));
    }

    private String seatLabel(int remaining) {
        return remaining >= 1 ? "残り " + remaining + " 席" : "満席";
    }
}
