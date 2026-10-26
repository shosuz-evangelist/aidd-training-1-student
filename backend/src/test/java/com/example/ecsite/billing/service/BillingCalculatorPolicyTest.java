package com.example.ecsite.billing.service;

import com.example.ecsite.billing.entity.BillingPlan;
import com.example.ecsite.billing.entity.TaxRounding;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** BillingCalculator の補助メソッド（税・年契約の割引・月の方針）の単体テスト。Spring は使わない。 */
class BillingCalculatorPolicyTest {

    private static BillingPlan plan(String code, long percent, TaxRounding rounding) {
        try {
            Constructor<BillingPlan> c = BillingPlan.class.getDeclaredConstructor();
            c.setAccessible(true);
            BillingPlan p = c.newInstance();
            set(p, "code", code);
            set(p, "annualDiscountPercent", percent);
            set(p, "taxRounding", rounding);
            return p;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field f = BillingPlan.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void 税は_切り捨てで_4387円に438円() {
        assertEquals(438, BillingCalculator.taxOf(4387, TaxRounding.FLOOR));
    }

    @Test
    void 税は_四捨五入で_4387円に439円() {
        assertEquals(439, BillingCalculator.taxOf(4387, TaxRounding.HALF_UP));
    }

    @Test
    void 税の境界() {
        assertEquals(0, BillingCalculator.taxOf(0, TaxRounding.FLOOR));
        assertEquals(0, BillingCalculator.taxOf(0, TaxRounding.HALF_UP));
        assertEquals(0, BillingCalculator.taxOf(5, TaxRounding.FLOOR));
        assertEquals(1, BillingCalculator.taxOf(5, TaxRounding.HALF_UP));
        assertEquals(0, BillingCalculator.taxOf(4, TaxRounding.HALF_UP));
    }

    @Test
    void 年契約の割引は_割り切れる場合の額() {
        assertEquals(1200, BillingCalculator.annualDiscountOf(8000, 15));
        assertEquals(500, BillingCalculator.annualDiscountOf(5000, 10));
        assertEquals(0, BillingCalculator.annualDiscountOf(0, 15));
    }

    @Test
    void 同じ方針のプランは_1つの方針になる() {
        BillingCalculator.Policy policy = BillingCalculator.policyOf(List.of(
                plan("LIGHT", 10, TaxRounding.FLOOR),
                plan("STANDARD", 10, TaxRounding.FLOOR)));
        assertEquals(new BillingCalculator.Policy(10, TaxRounding.FLOOR), policy);
    }

    @Test
    void プランが1つだけでも_1つの方針になる() {
        BillingCalculator.Policy policy = BillingCalculator.policyOf(List.of(
                plan("PREMIUM", 15, TaxRounding.HALF_UP)));
        assertEquals(new BillingCalculator.Policy(15, TaxRounding.HALF_UP), policy);
    }

    @Test
    void 方針が2つ以上あると_IllegalStateException() {
        assertThrows(IllegalStateException.class, () -> BillingCalculator.policyOf(List.of(
                plan("PREMIUM", 15, TaxRounding.HALF_UP),
                plan("STANDARD", 10, TaxRounding.FLOOR))));
    }
}
