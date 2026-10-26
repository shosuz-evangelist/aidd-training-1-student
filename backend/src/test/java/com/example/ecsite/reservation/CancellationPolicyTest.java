package com.example.ecsite.reservation;

import com.example.ecsite.reservation.service.CancelDecision;
import com.example.ecsite.reservation.service.CancellationPolicy;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationPolicyTest {

    private final CancellationPolicy policy = new CancellationPolicy();

    private static OffsetDateTime t(String text) {
        return OffsetDateTime.parse(text);
    }

    @Test
    void 開始日の前日は0円でキャンセルできる() {
        CancelDecision d = policy.decide(
                t("2026-10-31T12:00:00+09:00"), t("2026-11-01T10:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isTrue();
        assertThat(d.fee()).isZero();
    }

    @Test
    void 開始日の当日で開始前は受講料の50パーセント() {
        CancelDecision d = policy.decide(
                t("2026-11-01T09:59:59+09:00"), t("2026-11-01T10:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isTrue();
        assertThat(d.fee()).isEqualTo(2500);
    }

    @Test
    void 受講料1001円の50パーセントは500円に切り捨てる() {
        CancelDecision d = policy.decide(
                t("2026-11-01T09:00:00+09:00"), t("2026-11-01T10:00:00+09:00"), 1001);
        assertThat(d.cancellable()).isTrue();
        assertThat(d.fee()).isEqualTo(500);
    }

    @Test
    void 受講料が0円ならキャンセル料も0円() {
        CancelDecision d = policy.decide(
                t("2026-11-01T09:00:00+09:00"), t("2026-11-01T10:00:00+09:00"), 0);
        assertThat(d.cancellable()).isTrue();
        assertThat(d.fee()).isZero();
    }

    @Test
    void 開始時刻ちょうどはキャンセルできない() {
        CancelDecision d = policy.decide(
                t("2026-11-01T10:00:00+09:00"), t("2026-11-01T10:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isFalse();
    }

    @Test
    void 開始時刻より後はキャンセルできない() {
        CancelDecision d = policy.decide(
                t("2026-11-01T10:00:01+09:00"), t("2026-11-01T10:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isFalse();
        d = policy.decide(
                t("2026-11-05T10:00:00+09:00"), t("2026-11-01T10:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isFalse();
    }

    @Test
    void 開始が日本時間の0時ちょうどでも開始前の1秒前は0円() {
        CancelDecision d = policy.decide(
                t("2026-10-31T23:59:59+09:00"), t("2026-11-01T00:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isTrue();
        assertThat(d.fee()).isZero();
    }

    @Test
    void 開始が日本時間の0時ちょうどのときその時刻でキャンセルできない() {
        CancelDecision d = policy.decide(
                t("2026-11-01T00:00:00+09:00"), t("2026-11-01T00:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isFalse();
    }

    @Test
    void 日本時間の0時を過ぎると当日になり50パーセント() {
        CancelDecision d = policy.decide(
                t("2026-11-01T00:00:00+09:00"), t("2026-11-01T10:00:00+09:00"), 5000);
        assertThat(d.cancellable()).isTrue();
        assertThat(d.fee()).isEqualTo(2500);
    }

    @Test
    void 日本時間の0時の1秒前は前日で0円() {
        CancelDecision d = policy.decide(
                t("2026-10-31T23:59:59+09:00"), t("2026-11-01T10:00:00+09:00"), 5000);
        assertThat(d.fee()).isZero();
    }

    @Test
    void UTCで表した同じ時刻でも日本時間の日付で判定する() {
        // 2026-10-31T15:00:00Z は 日本時間の 11-01 00:00。UTC の日付では 10-31 だが当日
        CancelDecision today = policy.decide(
                t("2026-10-31T15:00:00Z"), t("2026-11-01T01:00:00Z"), 5000);
        assertThat(today.cancellable()).isTrue();
        assertThat(today.fee()).isEqualTo(2500);

        // 2026-10-31T14:59:59Z は 日本時間の 10-31 23:59:59 で前日
        CancelDecision before = policy.decide(
                t("2026-10-31T14:59:59Z"), t("2026-11-01T01:00:00Z"), 5000);
        assertThat(before.cancellable()).isTrue();
        assertThat(before.fee()).isZero();
    }

    @Test
    void 開始日もUTCではなく日本時間の日付で比べる() {
        // 開始 2026-10-31T16:00:00Z は日本時間の 11-01 01:00。UTC の日付では 10-31 で現在と同じ日に見える
        // 現在 2026-10-31T10:00:00Z は日本時間の 10-31 19:00 で、開始日 11-01 より前なので 0 円
        CancelDecision d = policy.decide(
                t("2026-10-31T10:00:00Z"), t("2026-10-31T16:00:00Z"), 5000);
        assertThat(d.cancellable()).isTrue();
        assertThat(d.fee()).isZero();
    }

    @Test
    void 連続講座でも受講料に回数を掛けず受講料そのものを基準にする() {
        // price は講座の受講料そのもの。10,000 円の連続講座は 5,000 円で、回数は引数にない
        CancelDecision d = policy.decide(
                t("2026-11-01T09:00:00+09:00"), t("2026-11-01T10:00:00+09:00"), 10000);
        assertThat(d.fee()).isEqualTo(5000);
    }
}
