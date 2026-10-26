package com.example.ecsite.billing;

import com.example.ecsite.billing.exception.BillingError;
import com.example.ecsite.billing.exception.BillingException;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** 要件 6 - 同じ月の請求書を 2 回作らない */
class DuplicateInvoiceTest extends BillingTestBase {

    @Test
    void 要件6_1_同じ月の請求書をもう一度作ると拒否される() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);
        invoice(s, "2026-10");

        assertRejected(() -> invoice(s, "2026-10"), BillingError.ALREADY_ISSUED);
        assertThat(data.invoiceCount(s, "2026-10")).isEqualTo(1);
    }

    @Test
    void 要件6_2_2人が同時に作っても1通だけ() throws Exception {
        long s = subscribe("STANDARD", "2026-09-01", false, false);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.issueInvoice(s, YearMonth.parse("2026-10"));
                    return "OK";
                } catch (BillingException e) {
                    return e.getError().name();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> r : results) outcomes.add(r.get());
        pool.shutdown();

        assertThat(outcomes).containsExactlyInAnyOrder("OK", BillingError.ALREADY_ISSUED.name());
        assertThat(data.invoiceCount(s, "2026-10")).isEqualTo(1);
    }
}
