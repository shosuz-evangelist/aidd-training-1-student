package com.example.ecsite.reservation.repository;

import com.example.ecsite.reservation.entity.CourseSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

    /** 同じ教室で [startsAt, endsAt) と重なる登録済みの回。終了と開始が同じなら重ならない。 */
    @Query("SELECT s FROM CourseSession s WHERE s.room = :room "
            + "AND s.startsAt < :endsAt AND :startsAt < s.endsAt")
    List<CourseSession> findRoomOverlaps(@Param("room") String room,
                                         @Param("startsAt") OffsetDateTime startsAt,
                                         @Param("endsAt") OffsetDateTime endsAt);

    /** 同じ講師で [startsAt, endsAt) と重なる登録済みの回。終了と開始が同じなら重ならない。 */
    @Query("SELECT s FROM CourseSession s WHERE s.instructor = :instructor "
            + "AND s.startsAt < :endsAt AND :startsAt < s.endsAt")
    List<CourseSession> findInstructorOverlaps(@Param("instructor") String instructor,
                                               @Param("startsAt") OffsetDateTime startsAt,
                                               @Param("endsAt") OffsetDateTime endsAt);
}
