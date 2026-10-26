package com.example.ecsite.billing.controller;

import com.example.ecsite.billing.dto.InvoiceView;
import com.example.ecsite.billing.dto.SubscriptionView;
import com.example.ecsite.billing.entity.BillingPlan;
import com.example.ecsite.billing.repository.BillingPlanRepository;
import com.example.ecsite.billing.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SubscriptionController {
    private final SubscriptionService service;
    private final BillingPlanRepository planRepository;

    public SubscriptionController(SubscriptionService service, BillingPlanRepository planRepository) {
        this.service = service;
        this.planRepository = planRepository;
    }

    public record SubscribeRequest(Long customerId, String planCode, LocalDate startedOn, boolean annual, boolean coupon) {}
    public record ChangePlanRequest(String planCode, LocalDate effectiveOn) {}
    public record CancelRequest(LocalDate cancelledOn) {}
    public record IssueRequest(String billingMonth) {}

    @GetMapping("/billing-plans")
    public List<Map<String, Object>> plans() {
        return planRepository.findAll().stream()
                .map((BillingPlan p) -> Map.<String, Object>of("code", p.getCode(), "name", p.getName(), "monthlyFee", p.getMonthlyFee()))
                .toList();
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionView> subscribe(@RequestBody SubscribeRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.subscribe(r.customerId(), r.planCode(), r.startedOn(), r.annual(), r.coupon()));
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionView> list(@RequestParam Long customerId) {
        return service.list(customerId);
    }

    @PostMapping("/subscriptions/{id}/change-plan")
    public SubscriptionView changePlan(@PathVariable Long id, @RequestBody ChangePlanRequest r) {
        return service.changePlan(id, r.planCode(), r.effectiveOn());
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public SubscriptionView cancel(@PathVariable Long id, @RequestBody CancelRequest r) {
        return service.cancel(id, r.cancelledOn());
    }

    @PostMapping("/subscriptions/{id}/invoices")
    public ResponseEntity<InvoiceView> issue(@PathVariable Long id, @RequestBody IssueRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issueInvoice(id, YearMonth.parse(r.billingMonth())));
    }

    @GetMapping("/subscriptions/{id}/invoices")
    public List<InvoiceView> invoices(@PathVariable Long id) {
        return service.invoices(id);
    }
}
