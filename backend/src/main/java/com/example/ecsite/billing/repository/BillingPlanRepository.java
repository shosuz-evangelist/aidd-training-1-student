package com.example.ecsite.billing.repository;

import com.example.ecsite.billing.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanRepository extends JpaRepository<BillingPlan, String> {
}
