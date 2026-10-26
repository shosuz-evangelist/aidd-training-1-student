package com.example.ecsite.approval.service;

import com.example.ecsite.approval.dto.HistoryView;
import com.example.ecsite.approval.dto.ItemInput;
import com.example.ecsite.approval.dto.PurchaseRequestListView;
import com.example.ecsite.approval.dto.PurchaseRequestView;
import com.example.ecsite.approval.entity.Delegation;
import com.example.ecsite.approval.entity.Employee;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.entity.PurchaseRequest;
import com.example.ecsite.approval.entity.PurchaseRequestItem;
import com.example.ecsite.approval.entity.RequestStatus;
import com.example.ecsite.approval.entity.ApprovalHistory;
import com.example.ecsite.approval.entity.HistoryAction;
import com.example.ecsite.approval.exception.ApprovalError;
import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.exception.EmployeeNotFoundException;
import com.example.ecsite.approval.exception.RequestNotFoundException;
import com.example.ecsite.approval.repository.ApprovalHistoryRepository;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import com.example.ecsite.approval.repository.EmployeeRepository;
import com.example.ecsite.approval.repository.PurchaseRequestRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class PurchaseRequestService {
    private static final Set<EmployeeRole> APPLICANT_ROLES =
            Set.of(EmployeeRole.STAFF, EmployeeRole.SECTION_MANAGER, EmployeeRole.DEPARTMENT_MANAGER);

    private final PurchaseRequestRepository requestRepository;
    private final EmployeeRepository employeeRepository;
    private final ApprovalHistoryRepository historyRepository;
    private final ApprovalPolicy policy;
    private final DelegationService delegationService;
    private final Clock clock;

    public PurchaseRequestService(PurchaseRequestRepository requestRepository, EmployeeRepository employeeRepository,
                                  ApprovalHistoryRepository historyRepository, ApprovalPolicy policy,
                                  DelegationService delegationService, Clock clock) {
        this.requestRepository = requestRepository;
        this.employeeRepository = employeeRepository;
        this.historyRepository = historyRepository;
        this.policy = policy;
        this.delegationService = delegationService;
        this.clock = clock;
    }

    /**
     * 明細を検証し、合計を申請金額にして、決裁権限表の最初の承認者の承認待ちで保存する。
     * 申請そのものは履歴に残さない。検証に通らないときは何も保存しない。
     */
    @Transactional
    public PurchaseRequestView submit(long applicantId, List<ItemInput> items) {
        validateItems(items);
        long total = policy.totalAmount(items);
        Employee applicant = employeeRepository.findById(applicantId)
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        if (!APPLICANT_ROLES.contains(applicant.getRole())) {
            throw new InvalidApprovalRequestException("この役割は申請できません");
        }
        EmployeeRole firstApprover = policy.route(applicant.getRole(), total).get(0);

        PurchaseRequest request = new PurchaseRequest(applicantId, total, RequestStatus.PENDING, firstApprover,
                OffsetDateTime.now(clock));
        items.forEach(item -> request.addItem(new PurchaseRequestItem(item.unitPrice(), item.quantity())));
        return PurchaseRequestView.of(requestRepository.save(request));
    }

    /**
     * 承認待ちの承認者として承認し、次の承認者の承認待ち、なければ承認済みに進める。
     * 条件付き UPDATE と履歴の追加は同じトランザクションで行い、履歴は更新が 1 行のときだけ追加する。
     */
    @Transactional
    public PurchaseRequestView approve(long requestId, long actorId) {
        PurchaseRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RequestNotFoundException("申請がありません"));
        Employee actor = employeeRepository.findById(actorId)
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        Employee applicant = employeeRepository.findById(request.getApplicantId())
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        Long onBehalfOf = checkOperation(request, applicant, actor);

        Optional<EmployeeRole> next = policy.next(applicant.getRole(), request.getTotalAmount(),
                request.getPendingRole());
        RequestStatus toStatus = next.isPresent() ? RequestStatus.PENDING : RequestStatus.APPROVED;
        return advanceWithHistory(request, RequestStatus.PENDING, request.getPendingRole(), actorId, onBehalfOf,
                toStatus, next.orElse(null), HistoryAction.APPROVE, null);
    }

    /**
     * 承認待ちの承認者として、理由をつけて差し戻しにする。確認の順序は承認と同じ(checkOperation)のあとで、
     * 前後の空白(半角と全角)を除いた理由が空なら A02。履歴には入力された理由をそのまま残す。
     */
    @Transactional
    public PurchaseRequestView returnRequest(long requestId, long actorId, String reason) {
        PurchaseRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RequestNotFoundException("申請がありません"));
        Employee actor = employeeRepository.findById(actorId)
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        Employee applicant = employeeRepository.findById(request.getApplicantId())
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        Long onBehalfOf = checkOperation(request, applicant, actor);
        if (reason == null || reason.strip().isEmpty()) {
            throw new ApprovalException(ApprovalError.A02);
        }
        return advanceWithHistory(request, RequestStatus.PENDING, request.getPendingRole(), actorId, onBehalfOf,
                RequestStatus.RETURNED, null, HistoryAction.RETURN, reason);
    }

    /**
     * 出し直し(5.2、5.3、7.7)の権限と状態の確認だけを行い、何も書き込まない。
     * 差し戻しでなければ A03、申請者本人でなければ A01。代理は認めないため、代理の検索はしない。
     * 出し直した後の承認待ちの承認者は未決定のため、承認待ちにする遷移と履歴の追加は
     * tasks.md の「保留する要件」(5.1、5.4)が決まるまで持たない。決まったら、この確認を遷移の前に呼ぶ。
     * 複数の原因が重なるときの順序は未決定のため、単一の原因だけを確かめる。
     */
    @Transactional(readOnly = true)
    public void checkResubmit(long requestId, long actorId) {
        PurchaseRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RequestNotFoundException("申請がありません"));
        checkResubmit(request, actorId);
    }

    private void checkResubmit(PurchaseRequest request, long actorId) {
        if (request.getStatus() != RequestStatus.RETURNED) {
            throw new ApprovalException(ApprovalError.A03);
        }
        if (request.getApplicantId() != actorId) {
            throw new ApprovalException(ApprovalError.A01);
        }
    }

    /**
     * 購買部の社員として、承認済みの申請を発注済みにする(6.1-6.5)。代理は認めない(7.7)ため、代理の検索はしない。
     * 購買部でなければ A01、承認済みでなければ A03。複数の原因が重なるときの順序は未決定のため、単一の原因だけを確かめる。
     * 条件付き UPDATE と履歴の追加は同じトランザクションで行い、履歴は更新が 1 行のときだけ追加する。
     */
    @Transactional
    public PurchaseRequestView order(long requestId, long actorId) {
        PurchaseRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RequestNotFoundException("申請がありません"));
        Employee actor = employeeRepository.findById(actorId)
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        checkOrder(request, actor);
        return advanceWithHistory(request, RequestStatus.APPROVED, null, actorId, null, RequestStatus.ORDERED, null,
                HistoryAction.ORDER, null);
    }

    private static void checkOrder(PurchaseRequest request, Employee actor) {
        if (actor.getRole() != EmployeeRole.PURCHASING) {
            throw new ApprovalException(ApprovalError.A01);
        }
        if (request.getStatus() != RequestStatus.APPROVED) {
            throw new ApprovalException(ApprovalError.A03);
        }
    }

    /**
     * 全ての申請を、閲覧者ごとの 4 つの操作の可否つきで返す(8.1-8.4)。可否は、承認・差し戻し・出し直し・発注が
     * 実際に呼ぶ確認(checkOperation、checkResubmit、checkOrder)をそのまま呼び、A01 か A03 で拒否されなければ true にする。
     * 差し戻しの理由(A02)は入力の検証で権限の確認ではないため、可否には含めない。閲覧者がいなければ EmployeeNotFoundException。
     */
    @Transactional(readOnly = true)
    public List<PurchaseRequestListView> list(long viewerId) {
        Employee viewer = employeeRepository.findById(viewerId)
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        return requestRepository.findAll(Sort.by("id")).stream().map(request -> {
            Employee applicant = employeeRepository.findById(request.getApplicantId())
                    .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
            boolean canOperate = passes(() -> checkOperation(request, applicant, viewer));
            return PurchaseRequestListView.of(request, canOperate, canOperate,
                    passes(() -> checkResubmit(request, viewerId)), passes(() -> checkOrder(request, viewer)));
        }).toList();
    }

    private static boolean passes(Runnable check) {
        try {
            check.run();
            return true;
        } catch (ApprovalException e) {
            return false;
        }
    }

    /** 申請の状態を条件付き UPDATE で 1 回だけ進め、更新が 1 行のときだけ履歴を追加する。 */
    private PurchaseRequestView advanceWithHistory(PurchaseRequest request, RequestStatus fromStatus,
                                                   EmployeeRole fromRole, long actorId, Long onBehalfOf,
                                                   RequestStatus toStatus, EmployeeRole toRole, HistoryAction action,
                                                   String reason) {
        long requestId = request.getId();
        int updated = requestRepository.advance(requestId, fromStatus, fromRole, toStatus, toRole);
        if (updated != 1) {
            throw new ApprovalException(ApprovalError.A03);
        }
        historyRepository.save(new ApprovalHistory(requestId, actorId, onBehalfOf, action, reason,
                OffsetDateTime.now(clock)));
        return PurchaseRequestView.of(requestRepository.findById(requestId).orElseThrow());
    }

    /** 申請の履歴を古い順に返す。申請がなければ RequestNotFoundException。 */
    @Transactional(readOnly = true)
    public List<HistoryView> history(long requestId) {
        if (requestRepository.findById(requestId).isEmpty()) {
            throw new RequestNotFoundException("申請がありません");
        }
        return historyRepository.findByRequestIdOrderByIdAsc(requestId).stream().map(HistoryView::of).toList();
    }

    /**
     * 承認と差し戻しで共通の、状態と権限の確認。承認待ちでなければ A03、権限がなければ A01。
     * 本人の資格があれば null(履歴の代理元は空)。なければ今日有効な代理のうち、代理元が資格を満たす最初のもの
     * (代理設定の ID の昇順)の代理元の ID を返す。見つからなければ A01。
     * 複数の原因が重なるときの順序は未決定のため、単一の原因だけを確かめる。
     */
    private Long checkOperation(PurchaseRequest request, Employee applicant, Employee actor) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ApprovalException(ApprovalError.A03);
        }
        if (policy.isEligibleApprover(request, applicant, actor)) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        return delegationService.activeFor(actor.getCustomerId(), today).stream()
                .sorted(Comparator.comparing(Delegation::getId))
                .map(Delegation::getDelegatorId)
                .filter(delegatorId -> employeeRepository.findById(delegatorId)
                        .map(delegator -> policy.isEligibleApprover(request, applicant, delegator))
                        .orElse(false))
                .findFirst()
                .orElseThrow(() -> new ApprovalException(ApprovalError.A01));
    }

    private static void validateItems(List<ItemInput> items) {
        if (items == null || items.isEmpty()) {
            throw new InvalidApprovalRequestException("明細が 1 件もありません");
        }
        for (ItemInput item : items) {
            if (item == null || item.quantity() < 1) {
                throw new InvalidApprovalRequestException("数量は 1 以上にしてください");
            }
            if (item.unitPrice() < 0) {
                throw new InvalidApprovalRequestException("単価は 0 以上にしてください");
            }
        }
    }
}
