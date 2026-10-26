package com.example.ecsite.billing.dto;

import java.time.LocalDate;
import java.util.List;

public record SubscriptionView(Long id, Long customerId, LocalDate startedOn, boolean annual, boolean coupon,
                               LocalDate cancelledOn, List<Period> periods) {
    public record Period(String planCode, String planName, LocalDate startsOn, LocalDate endsOn) {
    }
}
