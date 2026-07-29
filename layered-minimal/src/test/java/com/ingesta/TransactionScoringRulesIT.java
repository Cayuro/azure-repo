package com.ingesta;

import com.ingesta.model.TransactionScore;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionScoringRulesIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionScoreRepository scoreRepository;

    @Test
    void shouldActivateVelocityRule() throws Exception {
        String accountId = "acc-velocity";
        postTransaction("tx-velocity-1", accountId, 100.00, 4.7110, -74.0721, "retail", "2026-07-23T10:00:00Z");
        postTransaction("tx-velocity-2", accountId, 100.00, 4.7110, -74.0721, "retail", "2026-07-23T10:01:00Z");
        postTransaction("tx-velocity-3", accountId, 100.00, 4.7110, -74.0721, "retail", "2026-07-23T10:02:00Z");

        TransactionScore score = awaitScore("tx-velocity-3");
        assertEquals(35, score.score());
        assertTrue(score.activations().stream().anyMatch(activation -> activation.ruleId().equals("VELOCIDAD")));
    }

    @Test
    void shouldActivateAmountRule() throws Exception {
        String accountId = "acc-amount";
        postTransaction("tx-amount-1", accountId, 100.00, 4.7110, -74.0721, "retail", "2026-07-23T11:00:00Z");
        postTransaction("tx-amount-2", accountId, 600.00, 4.7110, -74.0721, "retail", "2026-07-23T11:01:00Z");

        TransactionScore score = awaitScore("tx-amount-2");
        assertEquals(30, score.score());
        assertTrue(score.activations().stream().anyMatch(activation -> activation.ruleId().equals("MONTO_ATIPICO")));
    }

    @Test
    void shouldActivateGeoImpossibleRule() throws Exception {
        String accountId = "acc-geo";
        postTransaction("tx-geo-1", accountId, 100.00, 4.7110, -74.0721, "retail", "2026-07-23T12:00:00Z");
        postTransaction("tx-geo-2", accountId, 100.00, 40.4168, -3.7038, "retail", "2026-07-23T12:01:00Z");

        TransactionScore score = awaitScore("tx-geo-2");
        assertEquals(17, score.score());
        assertTrue(score.activations().stream().anyMatch(activation -> activation.ruleId().equals("GEO_IMPOSIBLE")));
    }

    @Test
    void shouldActivateMerchantRiskRule() throws Exception {
        postTransaction("tx-risk-1", "acc-risk", 100.00, 4.7110, -74.0721, "gambling", "2026-07-23T13:00:00Z");

        TransactionScore score = awaitScore("tx-risk-1");
        assertEquals(20, score.score());
        assertTrue(score.activations().stream().anyMatch(activation -> activation.ruleId().equals("COMERCIO_RIESGO")));
    }

    private void postTransaction(String transactionId, String accountId, double amount, double latitude, double longitude, String merchantCategory, String occurredAt) throws Exception {
        String payload = """
                {
                  "transactionId": "%s",
                  "accountId": "%s",
                  "amount": %.2f,
                  "currency": "COP",
                  "occurredAt": "%s",
                  "latitude": %.4f,
                  "longitude": %.4f,
                  "merchantId": "%s",
                  "merchantCategory": "%s"
                }
                """.formatted(transactionId, accountId, amount, occurredAt, latitude, longitude, transactionId + "-merchant", merchantCategory);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());
    }

    private TransactionScore awaitScore(String transactionId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            var score = scoreRepository.findByTransactionId(transactionId);
            if (score.isPresent()) {
                return score.get();
            }
            Thread.sleep(50);
        }
        return scoreRepository.findByTransactionId(transactionId).orElseThrow();
    }
}