package com.example.ecsite.reservation.repository;

import com.example.ecsite.reservation.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /** 全回を一緒に読んだ講座の一覧。 */
    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.sessions")
    List<Course> findAllWithSessions();

    /** 全回を一緒に読んだ講座 1 件。 */
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.sessions WHERE c.id = :id")
    Optional<Course> findWithSessionsById(@Param("id") Long id);

    /** 残席があるときだけ reserved_count を 1 増やす。更新した行数（0 または 1）を返す。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Course c SET c.reservedCount = c.reservedCount + 1 "
            + "WHERE c.id = :courseId AND c.reservedCount < c.capacity")
    int tryAcquireSeat(@Param("courseId") Long courseId);

    /** reserved_count が 1 以上のときだけ 1 減らす。更新した行数（0 または 1）を返す。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Course c SET c.reservedCount = c.reservedCount - 1 "
            + "WHERE c.id = :courseId AND c.reservedCount > 0")
    int releaseSeat(@Param("courseId") Long courseId);
}
