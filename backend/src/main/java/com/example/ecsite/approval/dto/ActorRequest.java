package com.example.ecsite.approval.dto;

/** 承認・出し直し・発注の入力。操作者の社員 ID だけを受け取る。 */
public record ActorRequest(Long actorId) {
}
