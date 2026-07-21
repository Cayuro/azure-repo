package com.ingesta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postThenGetShouldWork() throws Exception {
        String payload = "{\n"
                + "  \"transactionId\": \"tx-1001\",\n"
                + "  \"accountId\": \"acc-77\",\n"
                + "  \"amount\": 1250.50,\n"
                + "  \"currency\": \"COP\",\n"
                + "  \"occurredAt\": \"2026-07-17T14:00:00Z\",\n"
                + "  \"latitude\": 4.7110,\n"
                + "  \"longitude\": -74.0721,\n"
                + "  \"merchantId\": \"m-990\",\n"
                + "  \"merchantCategory\": \"retail\"\n"
                + "}";

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value("tx-1001"))
                .andExpect(jsonPath("$.status").value("RECIBIDA"));

        mockMvc.perform(get("/api/v1/transactions/tx-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-1001"))
                .andExpect(jsonPath("$.merchantCategory").value("retail"));
    }

    @Test
    void missingTransactionPathShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isNotFound());
    }
}
