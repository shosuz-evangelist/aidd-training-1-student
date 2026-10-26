package com.example.ecsite.billing.dto;

import java.time.LocalDate;
import java.util.List;

public record InvoiceView(String billingMonth, List<Line> lines, long subtotal, long annualDiscount, long couponDiscount,
                          long afterDiscount, long tax, long total) {
    public record Line(String planCode, LocalDate fromDate, LocalDate toDate, int days, long amount) {
    }
}
