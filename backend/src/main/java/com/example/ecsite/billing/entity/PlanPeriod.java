package com.example.ecsite.billing.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/** 契約のうち、1 つのプランで使っていた期間。endsOn が null なら続いている。 */
@Entity
@Table(name = "plan_periods")
public class PlanPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_code", nullable = false)
    private BillingPlan plan;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    public Long getId() { return id; }
    public Subscription getSubscription() { return subscription; }
    public void setSubscription(Subscription subscription) { this.subscription = subscription; }
    public BillingPlan getPlan() { return plan; }
    public void setPlan(BillingPlan plan) { this.plan = plan; }
    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }
}
