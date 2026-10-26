package com.example.ecsite.approval.controller;

import com.example.ecsite.approval.dto.EmployeeView;
import com.example.ecsite.approval.service.EmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 画面が操作者の役割を知るための読み取り専用の API。書き込みは持たない。 */
@RestController
@RequestMapping("/api/approval")
public class EmployeeController {
    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping("/employees/{customerId}")
    public EmployeeView get(@PathVariable long customerId) {
        return service.find(customerId);
    }
}
