package com.example.ecsite.approval.dto;

import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.PurchaseRequest;
import com.example.ecsite.approval.entity.RequestStatus;

/** 購買申請の応答。項目は暫定で、承認一覧の可否のフラグは一覧の実装で足す。 */
public record PurchaseRequestView(Long id, Long applicantId, long totalAmount, RequestStatus status,
                                  EmployeeRole pendingRole) {
    public static PurchaseRequestView of(PurchaseRequest r) {
        return new PurchaseRequestView(r.getId(), r.getApplicantId(), r.getTotalAmount(), r.getStatus(),
                r.getPendingRole());
    }
}
