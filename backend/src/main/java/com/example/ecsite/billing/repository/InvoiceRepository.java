package com.example.ecsite.billing.repository;

import com.example.ecsite.billing.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    boolean existsBySubscriptionIdAndBillingMonth(Long subscriptionId, String billingMonth);

    List<Invoice> findBySubscriptionIdOrderByBillingMonth(Long subscriptionId);
}
