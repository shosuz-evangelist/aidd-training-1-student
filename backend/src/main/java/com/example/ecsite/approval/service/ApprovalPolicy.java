package com.example.ecsite.approval.service;

import com.example.ecsite.approval.dto.ItemInput;
import com.example.ecsite.approval.entity.Employee;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.PurchaseRequest;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.example.ecsite.approval.entity.EmployeeRole.DEPARTMENT_MANAGER;
import static com.example.ecsite.approval.entity.EmployeeRole.EXECUTIVE;
import static com.example.ecsite.approval.entity.EmployeeRole.SECTION_MANAGER;

/** 決裁権限表と権限の判定。リポジトリを持たず、渡された値だけで判定する。 */
@Component
public final class ApprovalPolicy {
    /** 申請金額の帯。10,000 円ちょうどは「1 万円以上 10 万円以下」、100,000 円と 1,000,000 円は下の帯。 */
    enum Band { UNDER_10K, FROM_10K_TO_100K, OVER_100K_TO_1M, OVER_1M }

    static Band bandOf(long amount) {
        if (amount < 10_000) {
            return Band.UNDER_10K;
        }
        if (amount <= 100_000) {
            return Band.FROM_10K_TO_100K;
        }
        if (amount <= 1_000_000) {
            return Band.OVER_100K_TO_1M;
        }
        return Band.OVER_1M;
    }

    /** 承認者の並び(左から順に承認する)。申請者が担当者・課長・部長以外のときは IllegalArgumentException。 */
    public List<EmployeeRole> route(EmployeeRole applicantRole, long amount) {
        Band band = bandOf(amount);
        return switch (applicantRole) {
            case STAFF -> switch (band) {
                case UNDER_10K, FROM_10K_TO_100K -> List.of(SECTION_MANAGER);
                case OVER_100K_TO_1M -> List.of(SECTION_MANAGER, DEPARTMENT_MANAGER);
                case OVER_1M -> List.of(SECTION_MANAGER, DEPARTMENT_MANAGER, EXECUTIVE);
            };
            case SECTION_MANAGER -> switch (band) {
                case UNDER_10K -> List.of(SECTION_MANAGER);
                case FROM_10K_TO_100K, OVER_100K_TO_1M -> List.of(DEPARTMENT_MANAGER);
                case OVER_1M -> List.of(DEPARTMENT_MANAGER, EXECUTIVE);
            };
            case DEPARTMENT_MANAGER -> band == Band.UNDER_10K
                    ? List.of(DEPARTMENT_MANAGER)
                    : List.of(EXECUTIVE);
            default -> throw new IllegalArgumentException("申請できない役割です: " + applicantRole);
        };
    }

    /** route の中で current の次の役割。最後、または current が並びにないときは empty。 */
    public Optional<EmployeeRole> next(EmployeeRole applicantRole, long amount, EmployeeRole current) {
        List<EmployeeRole> route = route(applicantRole, amount);
        int index = route.indexOf(current);
        if (index < 0 || index + 1 >= route.size()) {
            return Optional.empty();
        }
        return Optional.of(route.get(index + 1));
    }

    /** 明細の合計。単価 × 数量の和。桁あふれは Math.multiplyExact / addExact で検出する。 */
    public long totalAmount(List<ItemInput> items) {
        try {
            long total = 0;
            for (ItemInput item : items) {
                total = Math.addExact(total, Math.multiplyExact(item.unitPrice(), (long) item.quantity()));
            }
            return total;
        } catch (ArithmeticException e) {
            throw new InvalidApprovalRequestException("申請金額が大きすぎます");
        }
    }

    /**
     * 承認待ちの承認者として request を承認または差し戻せるか(本人の資格)。request.status が PENDING のときだけ呼ぶ。
     * 役割が承認待ちの役割と同じ、課長・部長は申請者と同じ部署(役員は部署を問わない)、
     * 本人が申請者のときは 1 万円未満だけ。
     */
    public boolean isEligibleApprover(PurchaseRequest request, Employee applicant, Employee approver) {
        if (approver.getRole() != request.getPendingRole()) {
            return false;
        }
        boolean needsSameDepartment = approver.getRole() == SECTION_MANAGER || approver.getRole() == DEPARTMENT_MANAGER;
        if (needsSameDepartment
                && (approver.getDepartment() == null || !approver.getDepartment().equals(applicant.getDepartment()))) {
            return false;
        }
        if (approver.getCustomerId().equals(applicant.getCustomerId())) {
            return bandOf(request.getTotalAmount()) == Band.UNDER_10K;
        }
        return true;
    }
}
