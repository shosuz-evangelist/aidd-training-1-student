package com.example.ecsite.billing.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    /** 請求する月（例: 2026-10） */
    @Column(name = "billing_month", nullable = false, length = 7)
    private String billingMonth;

    private long subtotal;
    @Column(name = "annual_discount")
    private long annualDiscount;
    @Column(name = "coupon_discount")
    private long couponDiscount;
    @Column(name = "after_discount")
    private long afterDiscount;
    private long tax;
    private long total;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fromDate ASC")
    private List<InvoiceLine> lines = new ArrayList<>();

    public Long getId() { return id; }
    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getBillingMonth() { return billingMonth; }
    public void setBillingMonth(String billingMonth) { this.billingMonth = billingMonth; }
    public long getSubtotal() { return subtotal; }
    public void setSubtotal(long subtotal) { this.subtotal = subtotal; }
    public long getAnnualDiscount() { return annualDiscount; }
    public void setAnnualDiscount(long annualDiscount) { this.annualDiscount = annualDiscount; }
    public long getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(long couponDiscount) { this.couponDiscount = couponDiscount; }
    public long getAfterDiscount() { return afterDiscount; }
    public void setAfterDiscount(long afterDiscount) { this.afterDiscount = afterDiscount; }
    public long getTax() { return tax; }
    public void setTax(long tax) { this.tax = tax; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(OffsetDateTime issuedAt) { this.issuedAt = issuedAt; }
    public List<InvoiceLine> getLines() { return lines; }
}
