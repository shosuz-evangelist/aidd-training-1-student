package com.example.ecsite.reservation.repository;

import com.example.ecsite.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** お客様の ACTIVE な予約。講座と全回を一緒に読む。キャンセル済みは含めない。 */
    @Query("SELECT DISTINCT r FROM Reservation r JOIN FETCH r.course c LEFT JOIN FETCH c.sessions "
            + "WHERE r.customerId = :customerId AND r.status = com.example.ecsite.reservation.entity.ReservationStatus.ACTIVE "
            + "ORDER BY r.reservedAt, r.id")
    List<Reservation> findActiveByCustomerId(@Param("customerId") Long customerId);

    /** ACTIVE のときだけ CANCELLED にしてキャンセル日時と料金を保存する。更新した行数（0 または 1）を返す。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = com.example.ecsite.reservation.entity.ReservationStatus.CANCELLED, "
            + "r.cancelledAt = :cancelledAt, r.cancelFee = :cancelFee "
            + "WHERE r.id = :id AND r.status = com.example.ecsite.reservation.entity.ReservationStatus.ACTIVE")
    int cancelIfActive(@Param("id") Long id,
                       @Param("cancelledAt") OffsetDateTime cancelledAt,
                       @Param("cancelFee") Long cancelFee);
}
