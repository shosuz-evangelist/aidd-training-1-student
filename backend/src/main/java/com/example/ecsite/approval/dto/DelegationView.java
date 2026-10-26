package com.example.ecsite.approval.dto;

import com.example.ecsite.approval.entity.Delegation;

import java.time.LocalDate;

public record DelegationView(Long id, Long delegatorId, Long delegateId, LocalDate startDate, LocalDate endDate) {
    public static DelegationView of(Delegation d) {
        return new DelegationView(d.getId(), d.getDelegatorId(), d.getDelegateId(), d.getStartDate(), d.getEndDate());
    }
}
