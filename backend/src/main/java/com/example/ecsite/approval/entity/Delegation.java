package com.example.ecsite.approval.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "delegation")
public class Delegation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delegator_id", nullable = false)
    private Long delegatorId;

    @Column(name = "delegate_id", nullable = false)
    private Long delegateId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    protected Delegation() {
    }

    public Delegation(Long delegatorId, Long delegateId, LocalDate startDate, LocalDate endDate) {
        this.delegatorId = delegatorId;
        this.delegateId = delegateId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() { return id; }
    public Long getDelegatorId() { return delegatorId; }
    public Long getDelegateId() { return delegateId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}
