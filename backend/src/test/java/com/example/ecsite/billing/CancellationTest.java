package com.example.ecsite.billing;

import com.example.ecsite.billing.exception.BillingError;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

/** 要件 5 - 解約した月は解約日まで日割りにする */
class CancellationTest extends BillingTestBase {

    @Test
    void 要件5_1_解約日までの10日分を日割りにする() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);
        service.cancel(s, LocalDate.parse("2026-10-10"));

        assertAmounts(invoice(s, "2026-10"), 1612, 0, 161, 1773);
    }

    @Test
    void 要件5_2_解約した翌月は請求書を作らない() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);
        service.cancel(s, LocalDate.parse("2026-10-10"));

        assertRejected(() -> service.issueInvoice(s, YearMonth.parse("2026-11")), BillingError.NOT_SUBSCRIBED_IN_MONTH);
    }
}
