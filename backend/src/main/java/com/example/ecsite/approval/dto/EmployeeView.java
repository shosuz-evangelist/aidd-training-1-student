package com.example.ecsite.approval.dto;

import com.example.ecsite.approval.entity.EmployeeRole;

/** 社員の ID(顧客の ID と同じ)・顧客の名前・部署・役割。役員だけ部署が null。 */
public record EmployeeView(Long id, String name, String department, EmployeeRole role) {
}
