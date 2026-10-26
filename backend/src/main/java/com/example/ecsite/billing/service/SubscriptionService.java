package com.example.ecsite.billing.service;

import com.example.ecsite.billing.dto.InvoiceView;
import com.example.ecsite.billing.dto.SubscriptionView;
import com.example.ecsite.billing.entity.BillingPlan;
import com.example.ecsite.billing.entity.Invoice;
import com.example.ecsite.billing.entity.InvoiceLine;
import com.example.ecsite.billing.entity.PlanPeriod;
import com.example.ecsite.billing.entity.Subscription;
import com.example.ecsite.billing.exception.BillingError;
import com.example.ecsite.billing.exception.BillingException;
import com.example.ecsite.billing.repository.BillingPlanRepository;
import com.example.ecsite.billing.repository.InvoiceRepository;
import com.example.ecsite.billing.repository.SubscriptionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final BillingPlanRepository planRepository;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, BillingPlanRepository planRepository,
                               InvoiceRepository invoiceRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionView subscribe(Long customerId, String planCode, LocalDate startedOn, boolean annual, boolean coupon) {
        if (startedOn == null) {
            throw new BillingException(BillingError.INVALID_DATE);
        }
        Subscription s = new Subscription();
        s.setCustomerId(customerId);
        s.setStartedOn(startedOn);
        s.setAnnual(annual);
        s.setCoupon(coupon);
        s.getPeriods().add(period(s, plan(planCode), startedOn));
        return toView(subscriptionRepository.saveAndFlush(s));
    }

    /** 変更日の前日で今の期間を終え、変更日から新しいプランの期間を始める（research.md 2）。 */
    @Transactional
    public SubscriptionView changePlan(Long subscriptionId, String planCode, LocalDate effectiveOn) {
        Subscription s = subscription(subscriptionId);
        BillingPlan plan = plan(planCode);
        PlanPeriod current = s.currentPeriod();
        if (current.getPlan().getCode().equals(plan.getCode())) {
            throw new BillingException(BillingError.SAME_PLAN);
        }
        if (effectiveOn == null || !effectiveOn.isAfter(current.getStartsOn())
                || (s.getCancelledOn() != null && effectiveOn.isAfter(s.getCancelledOn()))) {
            throw new BillingException(BillingError.INVALID_DATE);
        }
        current.setEndsOn(effectiveOn.minusDays(1));
        s.getPeriods().add(period(s, plan, effectiveOn));
        return toView(subscriptionRepository.saveAndFlush(s));
    }

    /** 解約日まで使える。今の期間を解約日で終える。 */
    @Transactional
    public SubscriptionView cancel(Long subscriptionId, LocalDate cancelledOn) {
        Subscription s = subscription(subscriptionId);
        PlanPeriod current = s.currentPeriod();
        if (cancelledOn == null || cancelledOn.isBefore(current.getStartsOn()) || s.getCancelledOn() != null) {
            throw new BillingException(BillingError.INVALID_DATE);
        }
        s.setCancelledOn(cancelledOn);
        current.setEndsOn(cancelledOn);
        return toView(subscriptionRepository.saveAndFlush(s));
    }

    /** FR-003〜FR-010: その月の請求書を 1 通だけ作る。 */
    @Transactional
    public InvoiceView issueInvoice(Long subscriptionId, YearMonth month) {
        Subscription s = subscription(subscriptionId);
        if (invoiceRepository.existsBySubscriptionIdAndBillingMonth(subscriptionId, month.toString())) {
            throw new BillingException(BillingError.ALREADY_ISSUED);
        }
        InvoiceView v = BillingCalculator.calculate(s, month);
        if (v == null) {
            throw new BillingException(BillingError.NOT_SUBSCRIBED_IN_MONTH);
        }
        Invoice invoice = new Invoice();
        invoice.setSubscriptionId(subscriptionId);
        invoice.setBillingMonth(v.billingMonth());
        invoice.setSubtotal(v.subtotal());
        invoice.setAnnualDiscount(v.annualDiscount());
        invoice.setCouponDiscount(v.couponDiscount());
        invoice.setAfterDiscount(v.afterDiscount());
        invoice.setTax(v.tax());
        invoice.setTotal(v.total());
        invoice.setIssuedAt(OffsetDateTime.now(clock));
        for (InvoiceView.Line l : v.lines()) {
            invoice.getLines().add(new InvoiceLine(invoice, l.planCode(), l.fromDate(), l.toDate(), l.days(), l.amount()));
        }
        try {
            invoiceRepository.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException e) {
            // 同時に作られて一意制約に違反した（research.md 3）
            throw new BillingException(BillingError.ALREADY_ISSUED);
        }
        return v;
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> invoices(Long subscriptionId) {
        return invoiceRepository.findBySubscriptionIdOrderByBillingMonth(subscriptionId).stream()
                .map(i -> new InvoiceView(i.getBillingMonth(),
                        i.getLines().stream().map(l -> new InvoiceView.Line(l.getPlanCode(), l.getFromDate(), l.getToDate(),
                                l.getDays(), l.getAmount())).toList(),
                        i.getSubtotal(), i.getAnnualDiscount(), i.getCouponDiscount(), i.getAfterDiscount(), i.getTax(),
                        i.getTotal()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionView> list(Long customerId) {
        return subscriptionRepository.findByCustomerIdOrderById(customerId).stream().map(SubscriptionService::toView).toList();
    }

    private static PlanPeriod period(Subscription s, BillingPlan plan, LocalDate startsOn) {
        PlanPeriod p = new PlanPeriod();
        p.setSubscription(s);
        p.setPlan(plan);
        p.setStartsOn(startsOn);
        return p;
    }

    private BillingPlan plan(String code) {
        return planRepository.findById(code).orElseThrow(() -> new BillingException(BillingError.PLAN_NOT_FOUND));
    }

    private Subscription subscription(Long id) {
        return subscriptionRepository.findById(id).orElseThrow(() -> new BillingException(BillingError.SUBSCRIPTION_NOT_FOUND));
    }

    private static SubscriptionView toView(Subscription s) {
        return new SubscriptionView(s.getId(), s.getCustomerId(), s.getStartedOn(), s.isAnnual(), s.isCoupon(),
                s.getCancelledOn(), s.getPeriods().stream()
                .map(p -> new SubscriptionView.Period(p.getPlan().getCode(), p.getPlan().getName(), p.getStartsOn(), p.getEndsOn()))
                .toList());
    }
}
