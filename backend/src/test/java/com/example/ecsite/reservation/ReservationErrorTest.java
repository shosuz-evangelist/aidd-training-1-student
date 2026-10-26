package com.example.ecsite.reservation;

import com.example.ecsite.exception.ApiExceptionHandler;
import com.example.ecsite.reservation.exception.ReservationError;
import com.example.ecsite.reservation.exception.ReservationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationErrorTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/throw/{code}")
        public void throwError(@PathVariable String code) {
            throw new ReservationException(ReservationError.valueOf(code));
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("R01", "満席です", 409),
                Arguments.of("R02", "受付を締め切りました", 409),
                Arguments.of("R03", "すでに予約しています", 409),
                Arguments.of("R04", "時間帯が重なります", 409),
                Arguments.of("R05", "開始後はキャンセルできません", 409),
                Arguments.of("R06", "予約が見つかりません", 404),
                Arguments.of("R07", "入力が正しくありません", 400),
                Arguments.of("R08", "教室が重なります", 409),
                Arguments.of("R09", "講師が重なります", 409));
    }

    @ParameterizedTest
    @MethodSource("table")
    void 例外はコードとメッセージとステータスで返る(String code, String message, int httpStatus) throws Exception {
        mockMvc.perform(get("/throw/" + code))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void エラーは9件ちょうどある() {
        assertEquals(9, ReservationError.values().length);
    }
}
