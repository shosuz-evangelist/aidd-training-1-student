package com.example.ecsite.approval.dto;

import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.PurchaseRequest;
import com.example.ecsite.approval.entity.RequestStatus;

/** 承認一覧の 1 件。表示項目は暫定で、閲覧者ごとの 4 つの操作の可否を付ける。 */
public record PurchaseRequestListView(Long id, Long applicantId, long totalAmount, RequestStatus status,
                                      EmployeeRole pendingRole, boolean canApprove, boolean canReturn,
                                      boolean canResubmit, boolean canOrder) {
    public static PurchaseRequestListView of(PurchaseRequest r, boolean canApprove, boolean canReturn,
                                             boolean canResubmit, boolean canOrder) {
        return new PurchaseRequestListView(r.getId(), r.getApplicantId(), r.getTotalAmount(), r.getStatus(),
                r.getPendingRole(), canApprove, canReturn, canResubmit, canOrder);
    }
}
