package com.example.ecsite.billing.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "billing_plans")
public class BillingPlan {
    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    /** 税抜きの月額（円） */
    @Column(name = "monthly_fee", nullable = false)
    private long monthlyFee;

    public String getCode() { return code; }
    public String getName() { return name; }
    public long getMonthlyFee() { return monthlyFee; }
}
