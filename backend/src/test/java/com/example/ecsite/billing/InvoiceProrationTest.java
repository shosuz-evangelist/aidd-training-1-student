package com.example.ecsite.billing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 1 - 1 か月使った月 / 要件 2 - 月の途中で申し込んだ月は日割り */
class InvoiceProrationTest extends BillingTestBase {

    @Test
    void 要件1_1_1か月使った月はスタンダードの月額と税() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);

        assertAmounts(invoice(s, "2026-10"), 5000, 0, 500, 5500);
    }

    @Test
    void 要件2_1_10月15日からは17日分を日割りにして切り捨てる() {
        long s = subscribe("STANDARD", "2026-10-15", false, false);

        assertAmounts(invoice(s, "2026-10"), 2741, 0, 274, 3015);
    }

    @Test
    void 要件2_2_2月は28日で日割りにする() {
        long s = subscribe("STANDARD", "2027-02-15", false, false);

        assertThat(invoice(s, "2027-02").subtotal()).isEqualTo(2500);
    }

    @Test
    void 要件2_3_月末の1日だけ使った月() {
        long s = subscribe("STANDARD", "2026-10-31", false, false);

        assertThat(invoice(s, "2026-10").subtotal()).isEqualTo(161);
    }
}
