package com.example.ecsite;

import com.example.ecsite.service.CartService;
import com.example.ecsite.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** EC のテストの共通の土台。テストごとに初期化し、サービスと MockMvc を取得する。 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class EcTestBase {
    @Autowired protected TestDataHelper data;
    @Autowired protected CartService cartService;
    @Autowired protected ProductService productService;
    @Autowired protected MockMvc mvc;

    @BeforeEach
    void resetData() {
        data.reset();
    }
}
