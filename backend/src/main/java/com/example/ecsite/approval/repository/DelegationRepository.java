package com.example.ecsite.approval.repository;

import com.example.ecsite.approval.entity.Delegation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DelegationRepository extends JpaRepository<Delegation, Long> {
    /** 開始日と終了日を含む期間に date が入る代理を返す。 */
    List<Delegation> findByDelegateIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long delegateId, LocalDate startDate, LocalDate endDate);
}
