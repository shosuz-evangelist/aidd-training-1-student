package com.example.ecsite.approval.controller;

import com.example.ecsite.approval.dto.ActorRequest;
import com.example.ecsite.approval.dto.HistoryView;
import com.example.ecsite.approval.dto.PurchaseRequestListView;
import com.example.ecsite.approval.dto.PurchaseRequestView;
import com.example.ecsite.approval.dto.ReturnRequest;
import com.example.ecsite.approval.dto.SubmitRequest;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import com.example.ecsite.approval.service.PurchaseRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 購買申請の API。業務の判定は PurchaseRequestService に任せ、入力の取り出しと応答の状態だけを持つ。 */
@RestController
@RequestMapping("/api/approval/requests")
public class PurchaseRequestController {
    private final PurchaseRequestService service;

    public PurchaseRequestController(PurchaseRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PurchaseRequestView> submit(@RequestBody SubmitRequest r) {
        if (r.applicantId() == null) {
            throw new InvalidApprovalRequestException("申請者を指定してください");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(r.applicantId(), r.toItems()));
    }

    @GetMapping
    public List<PurchaseRequestListView> list(@RequestParam long viewerId) {
        return service.list(viewerId);
    }

    @PostMapping("/{id}/approve")
    public PurchaseRequestView approve(@PathVariable long id, @RequestBody ActorRequest r) {
        return service.approve(id, requireActor(r.actorId()));
    }

    @PostMapping("/{id}/return")
    public PurchaseRequestView giveBack(@PathVariable long id, @RequestBody ReturnRequest r) {
        return service.returnRequest(id, requireActor(r.actorId()), r.reason());
    }

    /**
     * 出し直しの権限と状態の確認(A01・A03)までを行う。確認に通った後の承認待ちへの遷移と成功の応答は、
     * tasks.md の「保留する要件」(5.1、5.4)が決まるまで定めないため、通ったときは 501 で未対応を返し、何も書き込まない。
     */
    @PostMapping("/{id}/resubmit")
    public ResponseEntity<Map<String, String>> resubmit(@PathVariable long id, @RequestBody ActorRequest r) {
        service.checkResubmit(id, requireActor(r.actorId()));
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("message", "出し直しの承認待ちへの遷移は未対応です"));
    }

    @PostMapping("/{id}/order")
    public PurchaseRequestView order(@PathVariable long id, @RequestBody ActorRequest r) {
        return service.order(id, requireActor(r.actorId()));
    }

    @GetMapping("/{id}/history")
    public List<HistoryView> history(@PathVariable long id) {
        return service.history(id);
    }

    private static long requireActor(Long actorId) {
        if (actorId == null) {
            throw new InvalidApprovalRequestException("操作者を指定してください");
        }
        return actorId;
    }
}
