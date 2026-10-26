package com.example.ecsite.billing.repository;

import com.example.ecsite.billing.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByCustomerIdOrderById(Long customerId);
}
