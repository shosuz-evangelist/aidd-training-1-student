package com.example.ecsite.approval.service;

import com.example.ecsite.approval.dto.EmployeeView;
import com.example.ecsite.approval.entity.EmployeeRole;
import com.example.ecsite.approval.exception.EmployeeNotFoundException;
import com.example.ecsite.approval.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** 顧客の ID から社員を返す。名前は customer テーブルから ID で取る。社員でない顧客は見つからないものとして扱う。 */
    @Transactional(readOnly = true)
    public EmployeeView find(long customerId) {
        EmployeeRepository.EmployeeWithName row = employeeRepository.findWithName(customerId)
                .orElseThrow(() -> new EmployeeNotFoundException("社員がいません"));
        return new EmployeeView(row.getCustomerId(), row.getName(), row.getDepartment(), EmployeeRole.valueOf(row.getRole()));
    }
}
