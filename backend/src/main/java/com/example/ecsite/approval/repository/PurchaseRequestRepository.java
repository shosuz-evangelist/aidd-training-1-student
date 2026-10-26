package com.example.ecsite.approval.repository;

import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.PurchaseRequest;
import com.example.ecsite.approval.entity.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    /**
     * 読んだ時点の状態と承認待ちの役割が一致するときだけ、次の状態と役割に更新する。
     * 承認待ちでない申請の役割は NULL なので、NULL どうしも一致として比べる。
     * 戻り値は更新できた行数で、0 なら別の操作が先に状態を進めている。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PurchaseRequest r SET r.status = :toStatus, r.pendingRole = :toRole
            WHERE r.id = :id AND r.status = :fromStatus AND r.pendingRole IS NOT DISTINCT FROM :fromRole
            """)
    int advance(@Param("id") Long id,
                @Param("fromStatus") RequestStatus fromStatus, @Param("fromRole") EmployeeRole fromRole,
                @Param("toStatus") RequestStatus toStatus, @Param("toRole") EmployeeRole toRole);
}
