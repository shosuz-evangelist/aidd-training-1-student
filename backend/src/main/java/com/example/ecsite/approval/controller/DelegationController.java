package com.example.ecsite.approval.controller;

import com.example.ecsite.approval.dto.DelegationRequest;
import com.example.ecsite.approval.dto.DelegationView;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import com.example.ecsite.approval.service.DelegationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approval")
public class DelegationController {
    private final DelegationService service;

    public DelegationController(DelegationService service) {
        this.service = service;
    }

    @PostMapping("/delegations")
    public ResponseEntity<DelegationView> register(@RequestBody DelegationRequest r) {
        if (r.delegatorId() == null || r.delegateId() == null || r.startDate() == null || r.endDate() == null) {
            throw new InvalidApprovalRequestException("代理元・代理人・開始日・終了日を指定してください");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.register(r.delegatorId(), r.delegateId(), r.startDate(), r.endDate()));
    }
}
