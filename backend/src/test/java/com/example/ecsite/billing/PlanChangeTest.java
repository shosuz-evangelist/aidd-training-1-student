package com.example.ecsite.billing;

import com.example.ecsite.billing.dto.InvoiceView;
import com.example.ecsite.billing.exception.BillingError;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 3 - 月の途中でプランを変えた月は分けて日割りにする */
class PlanChangeTest extends BillingTestBase {

    @Test
    void 要件3_1_変更日の前日まではライト変更日からはスタンダード() {
        long s = subscribe("LIGHT", "2026-09-01", false, false);
        service.changePlan(s, "STANDARD", LocalDate.parse("2026-10-16"));

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.lines()).extracting(InvoiceView.Line::days).containsExactly(15, 16);
        assertThat(v.lines()).extracting(InvoiceView.Line::amount).containsExactly(1451L, 2580L);
        assertAmounts(v, 4031, 0, 403, 4434);
    }

    @Test
    void 要件3_2_翌月は変更後のプランの1か月分() {
        long s = subscribe("LIGHT", "2026-09-01", false, false);
        service.changePlan(s, "STANDARD", LocalDate.parse("2026-10-16"));

        assertThat(invoice(s, "2026-11").subtotal()).isEqualTo(5000);
    }

    @Test
    void 要件3_3_同じプランには変えられない() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);

        assertRejected(() -> service.changePlan(s, "STANDARD", LocalDate.parse("2026-10-16")), BillingError.SAME_PLAN);
    }
}
