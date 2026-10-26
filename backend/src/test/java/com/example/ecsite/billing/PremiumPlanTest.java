package com.example.ecsite.billing;

import com.example.ecsite.billing.controller.SubscriptionController;
import com.example.ecsite.billing.dto.InvoiceView;
import com.example.ecsite.billing.dto.SubscriptionView;
import com.example.ecsite.billing.exception.BillingError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** プレミアムプランの追加。要件 1 から 5 と 7 を確かめる。 */
class PremiumPlanTest extends BillingTestBase {
    @Autowired SubscriptionController controller;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 要件1_1_プランの一覧はライト_スタンダード_プレミアムをコードと名前と月額つきで返す() {
        List<Map<String, Object>> plans = controller.plans();

        assertThat(plans)
                .extracting(p -> p.get("code"), p -> p.get("name"), p -> p.get("monthlyFee"))
                .containsExactlyInAnyOrder(
                        tuple("LIGHT", "ライト", 3000L),
                        tuple("STANDARD", "スタンダード", 5000L),
                        tuple("PREMIUM", "プレミアム", 8000L));
    }

    @Test
    void 要件1_2_プレミアムの月額は税抜き8000円() {
        long fee = controller.plans().stream()
                .filter(p -> "PREMIUM".equals(p.get("code")))
                .map(p -> ((Number) p.get("monthlyFee")).longValue())
                .findFirst().orElseThrow();

        assertThat(fee).isEqualTo(8000L);
    }

    @Test
    void 要件2_2_プレミアムで申し込むと申し込んだ日からのプレミアムが履歴に残る() {
        SubscriptionView v = service.subscribe(customer, "PREMIUM", LocalDate.parse("2026-10-05"), false, false);

        assertThat(v.periods())
                .extracting(SubscriptionView.Period::planCode, SubscriptionView.Period::startsOn, SubscriptionView.Period::endsOn)
                .containsExactly(tuple("PREMIUM", LocalDate.parse("2026-10-05"), null));
    }

    @Test
    void 要件2_3_スタンダードからプレミアムへ変えると変更日で履歴が分かれる() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);

        SubscriptionView v = service.changePlan(s, "PREMIUM", LocalDate.parse("2026-10-16"));

        assertThat(v.periods())
                .extracting(SubscriptionView.Period::planCode, SubscriptionView.Period::startsOn, SubscriptionView.Period::endsOn)
                .containsExactly(
                        tuple("STANDARD", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-15")),
                        tuple("PREMIUM", LocalDate.parse("2026-10-16"), null));
    }

    @Test
    void 要件2_4_プレミアムからプレミアムへは変えられない() {
        long s = subscribe("PREMIUM", "2026-09-01", false, false);

        assertRejected(() -> service.changePlan(s, "PREMIUM", LocalDate.parse("2026-10-16")), BillingError.SAME_PLAN);
    }

    @Test
    void 要件3_1_9月から契約したプレミアムの10月の請求書は満額() {
        long s = subscribe("PREMIUM", "2026-09-01", false, false);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.afterDiscount()).isEqualTo(8000);
        assertAmounts(v, 8000, 0, 800, 8800);
    }

    @Test
    void 要件3_2_10月15日からのプレミアムは17日分を切り捨てて日割りする() {
        long s = subscribe("PREMIUM", "2026-10-15", false, false);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.subtotal()).isEqualTo(4387);
    }

    @Test
    void 要件3_3_プレミアムの初月はクーポンの500円を引く() {
        long s = subscribe("PREMIUM", "2026-10-01", false, true);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.couponDiscount()).isEqualTo(500);
        assertThat(v.afterDiscount()).isEqualTo(7500);
        assertAmounts(v, 8000, 500, 750, 8250);
    }

    @Test
    void 要件3_4_プレミアムの翌月はクーポンを引かない() {
        long s = subscribe("PREMIUM", "2026-10-01", false, true);
        invoice(s, "2026-10");

        InvoiceView v = invoice(s, "2026-11");

        assertThat(v.couponDiscount()).isZero();
        assertThat(v.subtotal()).isEqualTo(8000);
    }

    @Test
    void 要件3_5_月末のプレミアムはクーポンを引いても0円より小さくしない() {
        long s = subscribe("PREMIUM", "2026-10-31", false, true);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.subtotal()).isEqualTo(258);
        assertThat(v.afterDiscount()).isZero();
        assertThat(v.total()).isZero();
    }

    @Test
    void 要件4_1_9月から年契約したプレミアムの10月は15パーセントの1200円を引く() {
        long s = subscribe("PREMIUM", "2026-09-01", true, false);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.annualDiscount()).isEqualTo(1200);
        assertThat(v.afterDiscount()).isEqualTo(6800);
        assertAmounts(v, 8000, 1200, 680, 7480);
    }

    @Test
    void 要件4_2_年契約のプレミアムでクーポンを使うと6800円から6300円になる() {
        long s = subscribe("PREMIUM", "2026-10-01", true, true);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.annualDiscount()).isEqualTo(1200);
        assertThat(v.couponDiscount()).isEqualTo(500);
        assertThat(v.afterDiscount()).isEqualTo(6300);
        assertAmounts(v, 8000, 1700, 630, 6930);
    }

    @Test
    void 要件4_3_年契約の割引をクーポンより先に引く() {
        long s = subscribe("PREMIUM", "2026-10-01", true, true);

        InvoiceView v = invoice(s, "2026-10");

        // 年契約の割引はクーポン前の小計 8,000 円の 15% で 1,200 円。クーポンの 500 円は引いた後の 6,800 円から引く。
        assertThat(v.subtotal() - v.annualDiscount()).isEqualTo(6800);
        assertThat(v.subtotal() - v.annualDiscount() - v.couponDiscount()).isEqualTo(v.afterDiscount());
        assertThat(v.afterDiscount()).isEqualTo(6300);
    }

    @Test
    void 要件5_1_5_2_10月15日からのプレミアムの消費税は438_7円を四捨五入して439円() {
        long s = subscribe("PREMIUM", "2026-10-15", false, false);

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.afterDiscount()).isEqualTo(4387);
        assertThat(v.tax()).isEqualTo(439);
        assertAmounts(v, 4387, 0, 439, 4826);
    }

    @Test
    void 要件5_3_消費税は年契約の割引とクーポンを引いた後の金額にかける() {
        long s = subscribe("PREMIUM", "2026-10-01", true, true);

        InvoiceView v = invoice(s, "2026-10");

        // 8,000 円にかけると 800 円、年契約の割引後の 6,800 円にかけると 680 円。両方を引いた 6,300 円なら 630 円。
        assertThat(v.afterDiscount()).isEqualTo(6300);
        assertThat(v.tax()).isEqualTo(630);
        assertThat(v.total()).isEqualTo(6930);
    }

    @Test
    void 要件7_1_10月の請求書を作ったプレミアムの10月をもう一度作ると拒否される() {
        long s = subscribe("PREMIUM", "2026-09-01", false, false);
        invoice(s, "2026-10");

        assertRejected(() -> invoice(s, "2026-10"), BillingError.ALREADY_ISSUED);
        assertThat(data.invoiceCount(s, "2026-10")).isEqualTo(1);
    }

    @Test
    void 要件7_2_10月10日に解約したプレミアムの11月の請求書は作らない() {
        long s = subscribe("PREMIUM", "2026-09-01", false, false);
        service.cancel(s, LocalDate.parse("2026-10-10"));

        assertRejected(() -> invoice(s, "2026-11"), BillingError.NOT_SUBSCRIBED_IN_MONTH);
    }

    @Test
    void 要件7_3_9月1日から契約し10月10日に解約したプレミアムは10日分を日割りして2580円() {
        long s = subscribe("PREMIUM", "2026-09-01", false, false);
        service.cancel(s, LocalDate.parse("2026-10-10"));

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.subtotal()).isEqualTo(2580);
    }

    @Test
    void 要件7_4_請求書の明細にプレミアムの期間と日数と金額をほかのプランと同じ形で記録する() {
        long s = subscribe("PREMIUM", "2026-09-01", false, false);
        service.cancel(s, LocalDate.parse("2026-10-10"));

        InvoiceView v = invoice(s, "2026-10");

        assertThat(v.lines())
                .extracting(InvoiceView.Line::planCode, InvoiceView.Line::fromDate, InvoiceView.Line::toDate,
                        InvoiceView.Line::days, InvoiceView.Line::amount)
                .containsExactly(tuple("PREMIUM", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"), 10, 2580L));
    }

    @Test
    void 要件6_5_作った請求書の保存した金額はその後に読み直しても次の月の請求書を作っても変わらない() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);
        invoice(s, "2026-10");
        List<Map<String, Object>> amounts = storedAmounts(s, "2026-10");
        List<Map<String, Object>> lines = storedLines(s, "2026-10");
        // 作った時点の保存値が 5,000 / 0 / 500 / 5,500 であること
        assertThat(amounts).hasSize(1);
        assertThat(amounts.get(0))
                .containsEntry("subtotal", 5000L).containsEntry("annual_discount", 0L).containsEntry("coupon_discount", 0L)
                .containsEntry("after_discount", 5000L).containsEntry("tax", 500L).containsEntry("total", 5500L);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).containsEntry("plan_code", "STANDARD").containsEntry("days", 31).containsEntry("amount", 5000L);

        // 一覧で読み直す
        List<InvoiceView> listed = service.invoices(s);
        assertThat(listed).hasSize(1);
        assertAmounts(listed.get(0), 5000, 0, 500, 5500);
        assertThat(storedAmounts(s, "2026-10")).isEqualTo(amounts);
        assertThat(storedLines(s, "2026-10")).isEqualTo(lines);

        // 次の月の請求書を作った後でも 10 月の保存値は同じ
        invoice(s, "2026-11");
        assertThat(storedAmounts(s, "2026-10")).isEqualTo(amounts);
        assertThat(storedLines(s, "2026-10")).isEqualTo(lines);
    }

    @Test
    void 未決定の事項2_スタンダードとプレミアムが同じ月にある請求書は金額を出さずに止まり保存もしない() {
        long s = subscribe("STANDARD", "2026-09-01", false, false);
        service.changePlan(s, "PREMIUM", LocalDate.parse("2026-10-16"));

        assertThatThrownBy(() -> invoice(s, "2026-10")).isInstanceOf(IllegalStateException.class);
        assertThat(data.invoiceCount(s, "2026-10")).isZero();
    }

    private List<Map<String, Object>> storedAmounts(long subscription, String month) {
        return jdbc.queryForList(
                "SELECT subtotal, annual_discount, coupon_discount, after_discount, tax, total FROM invoices"
                        + " WHERE subscription_id = ? AND billing_month = ?", subscription, month);
    }

    private List<Map<String, Object>> storedLines(long subscription, String month) {
        return jdbc.queryForList(
                "SELECT l.plan_code, l.from_date, l.to_date, l.days, l.amount FROM invoice_lines l"
                        + " JOIN invoices i ON i.id = l.invoice_id"
                        + " WHERE i.subscription_id = ? AND i.billing_month = ? ORDER BY l.id", subscription, month);
    }
}
