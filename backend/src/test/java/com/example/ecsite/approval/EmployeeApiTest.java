package com.example.ecsite.approval;

import com.example.ecsite.TestDataHelper;
import com.example.ecsite.approval.entity.EmployeeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 8.1, 8.2, 8.3 - 画面が操作者の役割を知るための、社員の読み取り API。 */
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestDataHelper data;

    @BeforeEach
    void setUp() {
        data.reset();
    }

    @Test
    void 課長を指定すると名前と部署と役割が返る() throws Exception {
        long id = data.createEmployee(data.createCustomer("kacho@example.com"), "営業部", EmployeeRole.SECTION_MANAGER);

        mvc.perform(get("/api/approval/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("kacho@example.com"))
                .andExpect(jsonPath("$.department").value("営業部"))
                .andExpect(jsonPath("$.role").value("SECTION_MANAGER"));
    }

    @Test
    void 部署のない役員は部署が空で返る() throws Exception {
        long id = data.createEmployee(data.createCustomer("exec@example.com"), null, EmployeeRole.EXECUTIVE);

        mvc.perform(get("/api/approval/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value(nullValue()))
                .andExpect(jsonPath("$.role").value("EXECUTIVE"));
    }

    @Test
    void 社員でない顧客は見つからない() throws Exception {
        long id = data.createCustomer("plain@example.com");

        mvc.perform(get("/api/approval/employees/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }
}
