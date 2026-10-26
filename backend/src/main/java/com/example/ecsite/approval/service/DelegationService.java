package com.example.ecsite.approval.service;

import com.example.ecsite.approval.dto.DelegationView;
import com.example.ecsite.approval.entity.Delegation;
import com.example.ecsite.approval.exception.EmployeeNotFoundException;
import com.example.ecsite.approval.repository.DelegationRepository;
import com.example.ecsite.approval.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DelegationService {
    private final DelegationRepository delegationRepository;
    private final EmployeeRepository employeeRepository;

    public DelegationService(DelegationRepository delegationRepository, EmployeeRepository employeeRepository) {
        this.delegationRepository = delegationRepository;
        this.employeeRepository = employeeRepository;
    }

    /** 保存するだけにする。誰を代理人にできるか・期間の前後関係・期間の重なりは検証しない(未決定)。 */
    @Transactional
    public DelegationView register(long delegatorId, long delegateId, LocalDate startDate, LocalDate endDate) {
        if (!employeeRepository.existsById(delegatorId) || !employeeRepository.existsById(delegateId)) {
            throw new EmployeeNotFoundException("社員がいません");
        }
        return DelegationView.of(delegationRepository.save(new Delegation(delegatorId, delegateId, startDate, endDate)));
    }

    /** start <= today <= end の代理を返す(両端を含む)。 */
    @Transactional(readOnly = true)
    public List<Delegation> activeFor(long delegateId, LocalDate today) {
        return delegationRepository
                .findByDelegateIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(delegateId, today, today);
    }
}
