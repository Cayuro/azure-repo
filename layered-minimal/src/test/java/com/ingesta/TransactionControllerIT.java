package com.ingesta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

        @Autowired
        private ObjectMapper objectMapper;

    @Test
    void postThenGetShouldWork() throws Exception {
        String payload = """
                {
                  "transactionId": "tx-1001",
                  "accountId": "acc-77",
                  "amount": 1250.50,
                  "currency": "COP",
                  "occurredAt": "2026-07-17T14:00:00Z",
                  "latitude": 4.7110,
                  "longitude": -74.0721,
                  "merchantId": "m-990",
                  "merchantCategory": "retail"
                }
                """;

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
    void duplicateTransactionShouldKeepOriginalIngestedAt() throws Exception {
        String payload = """
                {
                  "transactionId": "tx-2002",
                  "accountId": "acc-88",
                  "amount": 250.00,
                  "currency": "COP",
                  "occurredAt": "2026-07-17T15:00:00Z",
                  "latitude": 4.7110,
                  "longitude": -74.0721,
                  "merchantId": "m-991",
                  "merchantCategory": "retail"
                }
                """;

        MvcResult firstResult = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECIBIDA"))
                .andReturn();

        JsonNode firstResponse = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        String firstIngestedAt = firstResponse.get("ingestedAt").asText();

        MvcResult secondResult = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("YA_RECIBIDA"))
                .andReturn();

        JsonNode secondResponse = objectMapper.readTree(secondResult.getResponse().getContentAsString());
        String secondIngestedAt = secondResponse.get("ingestedAt").asText();

        org.junit.jupiter.api.Assertions.assertEquals(firstIngestedAt, secondIngestedAt);
    }
}
