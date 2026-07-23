package com.ingesta;

import com.ingesta.repository.FraudCaseRepository;
import com.ingesta.repository.TransactionScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionScoringIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionScoreRepository scoreRepository;

    @Autowired
    private FraudCaseRepository fraudCaseRepository;

    @Test
    void shouldScoreTransactionAndOpenCaseWhenThresholdIsExceeded() throws Exception {
        String firstPayload = """
                {
                  "transactionId": "tx-s-1",
                  "accountId": "acc-score-1",
                  "amount": 100.00,
                  "currency": "COP",
                  "occurredAt": "2026-07-23T10:00:00Z",
                  "latitude": 4.7110,
                  "longitude": -74.0721,
                  "merchantId": "m-100",
                  "merchantCategory": "retail"
                }
                """;

        String secondPayload = """
                {
                  "transactionId": "tx-s-2",
                  "accountId": "acc-score-1",
                  "amount": 100.00,
                  "currency": "COP",
                  "occurredAt": "2026-07-23T10:01:00Z",
                  "latitude": 4.7111,
                  "longitude": -74.0722,
                  "merchantId": "m-101",
                  "merchantCategory": "retail"
                }
                """;

        String thirdPayload = """
                {
                  "transactionId": "tx-s-3",
                  "accountId": "acc-score-1",
                  "amount": 1000.00,
                  "currency": "COP",
                  "occurredAt": "2026-07-23T10:02:00Z",
                  "latitude": 40.4168,
                  "longitude": -3.7038,
                  "merchantId": "m-102",
                  "merchantCategory": "gambling"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdPayload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        awaitScore("tx-s-3");
        awaitFraudCase("tx-s-3");

        assertTrue(scoreRepository.findByTransactionId("tx-s-3").orElseThrow().score() > 60);
        assertTrue(fraudCaseRepository.findByTransactionId("tx-s-3").isPresent());
    }

    private void awaitScore(String transactionId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        while (Instant.now().isBefore(deadline)) {
            if (scoreRepository.findByTransactionId(transactionId).isPresent()) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private void awaitFraudCase(String transactionId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        while (Instant.now().isBefore(deadline)) {
            if (fraudCaseRepository.findByTransactionId(transactionId).isPresent()) {
                return;
            }
            Thread.sleep(50);
        }
    }
}