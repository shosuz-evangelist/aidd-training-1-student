package com.example.ecsite.billing.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(nullable = false)
    private int days;

    @Column(nullable = false)
    private long amount;

    public InvoiceLine() {
    }

    public InvoiceLine(Invoice invoice, String planCode, LocalDate fromDate, LocalDate toDate, int days, long amount) {
        this.invoice = invoice;
        this.planCode = planCode;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.days = days;
        this.amount = amount;
    }

    public String getPlanCode() { return planCode; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public int getDays() { return days; }
    public long getAmount() { return amount; }
}
