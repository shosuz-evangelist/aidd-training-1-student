package com.example.ecsite.billing;

import com.example.ecsite.billing.entity.BillingPlan;
import com.example.ecsite.billing.entity.TaxRounding;
import com.example.ecsite.billing.repository.BillingPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BillingPlanMappingTest extends BillingTestBase {
    @Autowired BillingPlanRepository plans;

    @Test
    void プレミアムは年額割引15パーセントと四捨五入を持つ() {
        BillingPlan premium = plans.findById("PREMIUM").orElseThrow();
        assertThat(premium.getAnnualDiscountPercent()).isEqualTo(15L);
        assertThat(premium.getTaxRounding()).isEqualTo(TaxRounding.HALF_UP);
    }

    @Test
    void スタンダードは年額割引10パーセントと切り捨てを持つ() {
        BillingPlan standard = plans.findById("STANDARD").orElseThrow();
        assertThat(standard.getAnnualDiscountPercent()).isEqualTo(10L);
        assertThat(standard.getTaxRounding()).isEqualTo(TaxRounding.FLOOR);
    }
}
