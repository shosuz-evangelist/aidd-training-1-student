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

    /** 年額払いの割引率（%） */
    @Column(name = "annual_discount_percent", nullable = false)
    private long annualDiscountPercent;

    /** 消費税の端数の処理方法 */
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_rounding", nullable = false)
    private TaxRounding taxRounding;

    public String getCode() { return code; }
    public String getName() { return name; }
    public long getMonthlyFee() { return monthlyFee; }
    public long getAnnualDiscountPercent() { return annualDiscountPercent; }
    public TaxRounding getTaxRounding() { return taxRounding; }
}
