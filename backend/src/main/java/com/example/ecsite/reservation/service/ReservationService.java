package com.example.ecsite.reservation.service;

import com.example.ecsite.reservation.dto.CancelResult;
import com.example.ecsite.reservation.dto.ReservationView;
import com.example.ecsite.reservation.dto.SessionView;
import com.example.ecsite.reservation.entity.Course;
import com.example.ecsite.reservation.entity.CourseSession;
import com.example.ecsite.reservation.entity.Reservation;
import com.example.ecsite.reservation.entity.ReservationStatus;
import com.example.ecsite.reservation.exception.ReservationError;
import com.example.ecsite.reservation.exception.ReservationException;
import com.example.ecsite.reservation.repository.CourseRepository;
import com.example.ecsite.reservation.repository.ReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ReservationService {
    private final CourseRepository courseRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;
    private final CancellationPolicy cancellationPolicy = new CancellationPolicy();

    public ReservationService(CourseRepository courseRepository, ReservationRepository reservationRepository,
                              Clock clock) {
        this.courseRepository = courseRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    /**
     * 予約を受け付ける。検査は 残席（R01）→ 締め切り（R02）→ 二重予約（R03）→ 時間帯の重なり（R04）の順で、最初の失敗を返す。
     * 席の確保は reserved_count の条件付き UPDATE で行い、0 行なら R01。部分一意索引の違反は R03。
     * エラーはトランザクションをロールバックし、予約の件数と reserved_count を変えない（4.4）。
     * 連続講座も 1 件の予約・1 席で全回を確保する（4.2）。
     */
    @Transactional
    public ReservationView reserve(long customerId, long courseId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Course course = courseRepository.findWithSessionsById(courseId).orElseThrow();

        // 1 残席。2 締め切り。講座一覧の予約ボタンと同じ Course.isOpenFor の条件を使い、残席を先に見る
        if (course.remainingSeats() < 1) {
            throw new ReservationException(ReservationError.R01);
        }
        if (!course.isOpenFor(now)) {
            throw new ReservationException(ReservationError.R02);
        }
        // 3 二重予約。4 時間帯の重なり。どちらも有効な予約だけを見る（キャンセル済みは含めない）
        List<Reservation> active = reservationRepository.findActiveByCustomerId(customerId);
        boolean alreadyReserved = active.stream()
                .anyMatch(r -> r.getCourse().getId().equals(courseId));
        if (alreadyReserved) {
            throw new ReservationException(ReservationError.R03);
        }
        // 連続講座は全回の時間帯を比べ、どれか 1 組でも重なれば重なる。接するだけ（終了 = 開始）は重ならない
        List<TimeRange> target = sessionRanges(course);
        boolean overlaps = active.stream()
                .flatMap(r -> sessionRanges(r.getCourse()).stream())
                .anyMatch(booked -> target.stream().anyMatch(t -> t.overlaps(booked)));
        if (overlaps) {
            throw new ReservationException(ReservationError.R04);
        }

        // 席の確保。更新後は永続化コンテキストが空になるため、講座を読み直す
        if (courseRepository.tryAcquireSeat(courseId) == 0) {
            throw new ReservationException(ReservationError.R01);
        }
        Course reloaded = courseRepository.findWithSessionsById(courseId).orElseThrow();

        Reservation reservation = new Reservation();
        reservation.setCustomerId(customerId);
        reservation.setCourse(reloaded);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setReservedAt(now);
        try {
            reservation = reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // 同じお客様の同じ講座を同時に予約した場合。例外でトランザクション全体を戻すため reserved_count も戻る
            throw new ReservationException(ReservationError.R03);
        }
        return toView(reservation, reloaded, now);
    }

    /**
     * お客様本人の ACTIVE な予約を、講座名・日程・受講料・現在のキャンセル料・キャンセルの可否とともに返す（5.1 から 5.5）。
     * キャンセル料と可否は CancellationPolicy から求め、キャンセルできないときの料金は 0。キャンセル済みは含めない。
     */
    @Transactional(readOnly = true)
    public List<ReservationView> list(long customerId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return reservationRepository.findActiveByCustomerId(customerId).stream()
                .map(r -> toView(r, r.getCourse(), now))
                .toList();
    }

    /**
     * 予約をキャンセルする。検査は 本人の確認（R06）→ 開始時刻の確認（R05）→ 料金の算出の順。
     * 他人の予約と存在しない予約はどちらも R06 とし、他人の予約の有無や開始の前後を知らせない。
     * ACTIVE のときだけ条件付きで CANCELLED にして料金を保存し、reserved_count を 1 減らす。
     * 料金は講座の受講料（連続講座も全回分）を基準にする。
     */
    @Transactional
    public CancelResult cancel(long customerId, long reservationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Reservation reservation = reservationRepository.findById(reservationId)
                .filter(r -> r.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ReservationException(ReservationError.R06));
        Course course = courseRepository.findWithSessionsById(reservation.getCourse().getId()).orElseThrow();

        CancelDecision decision = cancellationPolicy.decide(now, course.firstSessionStart(), course.getPrice());
        if (!decision.cancellable()) {
            throw new ReservationException(ReservationError.R05);
        }

        // 更新後は永続化コンテキストが空になるため、必要な値は先に取り出してある
        // 0 行（ACTIVE ではない）の場合の応答は未決定であり、requirements.md への追記を待つ。
        // ここでは応答を決めず、例外で処理を止めてトランザクションを巻き戻す。席は 1 行更新できた場合だけ戻す
        if (reservationRepository.cancelIfActive(reservationId, now, decision.fee()) != 1) {
            throw new IllegalStateException("予約が ACTIVE ではありません");
        }
        courseRepository.releaseSeat(course.getId());
        return new CancelResult(reservationId, decision.fee());
    }

    private static List<TimeRange> sessionRanges(Course course) {
        return course.getSessions().stream()
                .map(s -> new TimeRange(s.getStartsAt(), s.getEndsAt()))
                .toList();
    }

    private ReservationView toView(Reservation reservation, Course course, OffsetDateTime now) {
        List<SessionView> sessions = course.getSessions().stream()
                .sorted(Comparator.comparingInt(CourseSession::getSessionNo))
                .map(s -> new SessionView(s.getStartsAt(), s.getEndsAt(), s.getRoom(), s.getInstructor()))
                .toList();
        CancelDecision decision = cancellationPolicy.decide(now, course.firstSessionStart(), course.getPrice());
        long fee = decision.cancellable() ? decision.fee() : 0;
        return new ReservationView(reservation.getId(), course.getId(), course.getName(), sessions,
                course.getPrice(), fee, decision.cancellable());
    }
}
