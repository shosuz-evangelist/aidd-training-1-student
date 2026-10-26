package com.example.ecsite.approval;

import com.example.ecsite.approval.exception.ApprovalError;
import com.example.ecsite.approval.exception.ApprovalException;
import com.example.ecsite.approval.exception.InvalidApprovalRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 要件 1.4, 1.5, 1.6, 3.4, 3.6, 4.2 - 業務の拒否が既存と同じ { code, message } で返り、コードなしの 400 も返る。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApprovalExceptionHandlerTest.ThrowingConfig.class)
class ApprovalExceptionHandlerTest {
    @Autowired MockMvc mvc;

    @TestConfiguration
    static class ThrowingConfig {
        @RestController
        static class ThrowingController {
            @GetMapping("/test-only/approval-error/{code}")
            void throwError(@PathVariable ApprovalError code) {
                throw new ApprovalException(code);
            }

            @GetMapping("/test-only/approval-invalid")
            void throwInvalid() {
                throw new InvalidApprovalRequestException("明細が正しくありません");
            }
        }
    }

    @Test
    void A01は403で権限がないメッセージを返す() throws Exception {
        mvc.perform(get("/test-only/approval-error/A01"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A01"))
                .andExpect(jsonPath("$.message").value("操作する権限がありません"));
    }

    @Test
    void A02は400で理由を入力するメッセージを返す() throws Exception {
        mvc.perform(get("/test-only/approval-error/A02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A02"))
                .andExpect(jsonPath("$.message").value("差し戻しの理由を入力してください"));
    }

    @Test
    void A03は409で今の状態ではできないメッセージを返す() throws Exception {
        mvc.perform(get("/test-only/approval-error/A03"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("A03"))
                .andExpect(jsonPath("$.message").value("今の状態ではこの操作はできません"));
    }

    @Test
    void コードなしの例外は400でcodeを持たない() throws Exception {
        mvc.perform(get("/test-only/approval-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").value("明細が正しくありません"));
    }
}
