package com.example.ecsite.billing;

import com.example.ecsite.billing.dto.InvoiceView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 4 - 年契約の割引とクーポンを決まった順に引く */
class DiscountTest extends BillingTestBase {

    @Test
    void 要件4_1_年契約は小計の10パーセントを引く() {
        long s = subscribe("STANDARD", "2026-09-01", true, false);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.afterDiscount()).isEqualTo(4500);
        assertAmounts(v, 5000, 500, 450, 4950);
    }

    @Test
    void 要件4_2_年契約の割引を先にクーポンを後に引く() {
        long s = subscribe("STANDARD", "2026-10-01", true, true);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.annualDiscount()).isEqualTo(500);
        assertThat(v.couponDiscount()).isEqualTo(500);
        assertThat(v.afterDiscount()).isEqualTo(4000);
        assertAmounts(v, 5000, 1000, 400, 4400);
    }

    @Test
    void 要件4_3_クーポンは申し込んだ月だけ() {
        long s = subscribe("STANDARD", "2026-10-01", true, true);
        invoice(s, "2026-10");

        InvoiceView v = invoice(s, "2026-11");

        assertThat(v.couponDiscount()).isZero();
        assertThat(v.total()).isEqualTo(4950);
    }

    @Test
    void 要件6_4_ライトの年契約の割引は10パーセント() {
        long s = subscribe("LIGHT", "2026-09-01", true, false);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.annualDiscount()).isEqualTo(300);
        assertAmounts(v, 3000, 300, 270, 2970);
    }

    @Test
    void 要件4_4_割引の後は0円より小さくしない() {
        long s = subscribe("STANDARD", "2026-10-31", false, true);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.afterDiscount()).isZero();
        assertThat(v.total()).isZero();
    }
}
