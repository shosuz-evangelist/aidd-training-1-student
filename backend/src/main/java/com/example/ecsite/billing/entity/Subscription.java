package com.example.ecsite.billing.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** お客様は EC のドメインなので、ID だけで持つ（steering の structure.md）。 */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(nullable = false)
    private boolean annual;

    @Column(nullable = false)
    private boolean coupon;

    /** 解約した日。この日まで使える。 */
    @Column(name = "cancelled_on")
    private LocalDate cancelledOn;

    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startsOn ASC")
    private List<PlanPeriod> periods = new ArrayList<>();

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public LocalDate getStartedOn() { return startedOn; }
    public void setStartedOn(LocalDate startedOn) { this.startedOn = startedOn; }
    public boolean isAnnual() { return annual; }
    public void setAnnual(boolean annual) { this.annual = annual; }
    public boolean isCoupon() { return coupon; }
    public void setCoupon(boolean coupon) { this.coupon = coupon; }
    public LocalDate getCancelledOn() { return cancelledOn; }
    public void setCancelledOn(LocalDate cancelledOn) { this.cancelledOn = cancelledOn; }
    public List<PlanPeriod> getPeriods() { return periods; }

    public PlanPeriod currentPeriod() {
        return periods.get(periods.size() - 1);
    }
}
