package com.ingesta;

import com.ingesta.controller.WebController;
import com.ingesta.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageShouldRender() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void createTransactionShouldReturnHomeView() throws Exception {
        mockMvc.perform(post("/")
                        .param("transactionId", "tx-web")
                        .param("accountId", "acc-web")
                        .param("amount", "1250.50")
                        .param("currency", "COP")
                        .param("occurredAt", "2026-07-21T12:00:00Z")
                        .param("latitude", "4.7110")
                        .param("longitude", "-74.0721")
                        .param("merchantId", "m-web")
                        .param("merchantCategory", "retail"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void findTransactionShouldReturnHomeView() throws Exception {
        mockMvc.perform(get("/transaction").param("transactionId", "tx-web"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }
}
