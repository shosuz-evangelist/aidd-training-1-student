package com.example.ecsite.approval.entity;

import jakarta.persistence.*;

/** 社員。customer の ID を主キーとして持つ参照専用の記録。役員だけ部署が null。 */
@Entity
@Table(name = "employee")
public class Employee {
    @Id
    @Column(name = "customer_id")
    private Long customerId;

    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeRole role;

    protected Employee() {
    }

    public Employee(Long customerId, String department, EmployeeRole role) {
        this.customerId = customerId;
        this.department = department;
        this.role = role;
    }

    public Long getCustomerId() { return customerId; }
    public String getDepartment() { return department; }
    public EmployeeRole getRole() { return role; }
}
