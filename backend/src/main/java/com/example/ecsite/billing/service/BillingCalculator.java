package com.example.ecsite.billing.service;

import com.example.ecsite.billing.dto.InvoiceView;
import com.example.ecsite.billing.entity.BillingPlan;
import com.example.ecsite.billing.entity.PlanPeriod;
import com.example.ecsite.billing.entity.Subscription;
import com.example.ecsite.billing.entity.TaxRounding;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 請求額の計算（spec の「請求額の求め方」）。データベースには触らない。
 * 1. 日割り → 2. 年契約の割引 → クーポン（0 円が下限）→ 3. 消費税 → 4. 請求額 の順に求める。
 * 1 円未満は、日割り・年契約の割引・消費税のそれぞれで切り捨てる（long の割り算）。
 */
public final class BillingCalculator {
    public static final long COUPON_AMOUNT = 500;
    public static final long TAX_PERCENT = 10;

    private BillingCalculator() {
    }

    /** その月に 1 日も契約していなければ null を返す。 */
    public static InvoiceView calculate(Subscription subscription, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        int daysInMonth = month.lengthOfMonth();

        // 1. 日割り（プランの期間ごと）
        List<InvoiceView.Line> lines = new ArrayList<>();
        List<BillingPlan> plansInMonth = new ArrayList<>();
        for (PlanPeriod p : subscription.getPeriods()) {
            LocalDate end = p.getEndsOn() != null ? p.getEndsOn() : subscription.getCancelledOn();
            LocalDate from = later(p.getStartsOn(), monthStart);
            LocalDate to = end == null ? monthEnd : earlier(end, monthEnd);
            if (from.isAfter(to)) {
                continue;
            }
            int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
            long amount = p.getPlan().getMonthlyFee() * days / daysInMonth;
            lines.add(new InvoiceView.Line(p.getPlan().getCode(), from, to, days, amount));
            plansInMonth.add(p.getPlan());
        }
        if (lines.isEmpty()) {
            return null;
        }
        long subtotal = lines.stream().mapToLong(InvoiceView.Line::amount).sum();

        // 2. 割引: 年契約の割引を先に、クーポンを後に。0 円より小さくしない
        Policy policy = policyOf(plansInMonth);
        long annualDiscount = subscription.isAnnual() ? annualDiscountOf(subtotal, policy.annualDiscountPercent()) : 0;
        long afterAnnual = subtotal - annualDiscount;
        boolean couponMonth = subscription.isCoupon() && YearMonth.from(subscription.getStartedOn()).equals(month);
        long couponDiscount = couponMonth ? Math.min(COUPON_AMOUNT, afterAnnual) : 0;
        long afterDiscount = afterAnnual - couponDiscount;

        // 3. 消費税  4. 請求額
        long tax = taxOf(afterDiscount, policy.taxRounding());
        long total = afterDiscount + tax;
        return new InvoiceView(month.toString(), lines, subtotal, annualDiscount, couponDiscount, afterDiscount, tax, total);
    }

    /** 月の中で適用する年契約の割引率と税の端数処理。 */
    record Policy(long annualDiscountPercent, TaxRounding taxRounding) {
    }

    /** 割引後の金額から消費税を求める。FLOOR は切り捨て、HALF_UP は四捨五入。 */
    static long taxOf(long afterDiscount, TaxRounding rounding) {
        return switch (rounding) {
            case FLOOR -> afterDiscount * TAX_PERCENT / 100;
            case HALF_UP -> (afterDiscount * TAX_PERCENT + 50) / 100;
        };
    }

    /**
     * 小計と割引率から年契約の割引額を求める。
     * 1 円未満の端数の処理は暫定で切り捨て（未決定の事項 1）。決まったらここだけを差し替える。
     */
    static long annualDiscountOf(long subtotal, long percent) {
        return subtotal * percent / 100;
    }

    /**
     * 月の中のプランが 1 つの (割引率, 税の端数処理) を共有していればそれを返す。
     * 2 つ以上あるときは暫定で IllegalStateException（未決定の事項 2）。
     */
    static Policy policyOf(List<BillingPlan> plansInMonth) {
        List<Policy> distinct = plansInMonth.stream()
                .map(p -> new Policy(p.getAnnualDiscountPercent(), p.getTaxRounding()))
                .distinct()
                .toList();
        if (distinct.size() != 1) {
            throw new IllegalStateException("月の中のプランの方針が 1 つに定まらない: " + distinct);
        }
        return distinct.get(0);
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
