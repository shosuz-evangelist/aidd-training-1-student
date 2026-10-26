package com.example.ecsite.reservation.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** キャンセルの可否とキャンセル料の算出。データベースに触らない純粋な計算。 */
public final class CancellationPolicy {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /**
     * now は Clock から取った現在日時。firstStart は第 1 回の開始日時。
     * price は講座の受講料そのもの（連続講座でも回数を掛けない。材料費は含めない）。
     */
    public CancelDecision decide(OffsetDateTime now, OffsetDateTime firstStart, long price) {
        if (!now.isBefore(firstStart)) {
            return new CancelDecision(false, 0);
        }
        LocalDate today = now.atZoneSameInstant(JST).toLocalDate();
        LocalDate startDate = firstStart.atZoneSameInstant(JST).toLocalDate();
        long fee = today.isBefore(startDate) ? 0 : price * 50 / 100;
        return new CancelDecision(true, fee);
    }
}
