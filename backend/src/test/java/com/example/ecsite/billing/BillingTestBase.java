package com.example.ecsite.billing;

import com.example.ecsite.TestDataHelper;
import com.example.ecsite.billing.dto.InvoiceView;
import com.example.ecsite.billing.exception.BillingError;
import com.example.ecsite.billing.exception.BillingException;
import com.example.ecsite.billing.service.SubscriptionService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
abstract class BillingTestBase {
    @Autowired SubscriptionService service;
    @Autowired TestDataHelper data;

    long customer;

    @BeforeEach
    void setUpBase() {
        data.reset();
        customer = data.createCustomer("a@example.com");
    }

    long subscribe(String plan, String startedOn, boolean annual, boolean coupon) {
        return service.subscribe(customer, plan, LocalDate.parse(startedOn), annual, coupon).id();
    }

    InvoiceView invoice(long subscription, String month) {
        return service.issueInvoice(subscription, YearMonth.parse(month));
    }

    static void assertAmounts(InvoiceView v, long subtotal, long discount, long tax, long total) {
        assertThat(v.subtotal()).as("小計").isEqualTo(subtotal);
        assertThat(v.annualDiscount() + v.couponDiscount()).as("割引").isEqualTo(discount);
        assertThat(v.tax()).as("消費税").isEqualTo(tax);
        assertThat(v.total()).as("請求額").isEqualTo(total);
    }

    static void assertRejected(ThrowingCallable call, BillingError error) {
        assertThatThrownBy(call)
                .isInstanceOf(BillingException.class)
                .extracting(e -> ((BillingException) e).getError()).isEqualTo(error);
    }
}
