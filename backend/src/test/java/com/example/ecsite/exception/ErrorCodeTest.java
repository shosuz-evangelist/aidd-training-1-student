package com.example.ecsite.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void E01からE05が表のとおりの状態とメッセージを持つ() {
        assertThat(ErrorCode.INVALID_QUANTITY.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVALID_QUANTITY.getMessage()).isEqualTo("数量が正しくありません");
        assertThat(ErrorCode.INSUFFICIENT_STOCK.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INSUFFICIENT_STOCK.getMessage()).isEqualTo("在庫が足りません");
        assertThat(ErrorCode.PRODUCT_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.PRODUCT_NOT_FOUND.getMessage()).isEqualTo("商品がありません");
        assertThat(ErrorCode.NOT_IN_CART.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.NOT_IN_CART.getMessage()).isEqualTo("カートに入っていません");
        assertThat(ErrorCode.CART_EMPTY.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.CART_EMPTY.getMessage()).isEqualTo("カートが空です");
    }
}
