package com.example.ecsite.approval.repository;

import com.example.ecsite.approval.entity.ApprovalHistory;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/** 履歴は追記だけなので、保存と検索だけを公開する(更新と削除の操作は持たない)。 */
public interface ApprovalHistoryRepository extends Repository<ApprovalHistory, Long> {
    ApprovalHistory save(ApprovalHistory history);

    Optional<ApprovalHistory> findById(Long id);

    List<ApprovalHistory> findByRequestIdOrderByIdAsc(Long requestId);
}
