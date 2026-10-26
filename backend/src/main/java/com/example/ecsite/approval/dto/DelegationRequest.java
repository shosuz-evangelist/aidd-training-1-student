package com.example.ecsite.approval.dto;

import java.time.LocalDate;

public record DelegationRequest(Long delegatorId, Long delegateId, LocalDate startDate, LocalDate endDate) {}
