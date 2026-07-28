package com.ingesta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ingesta.ratelimit.max-requests=3",
        "ingesta.ratelimit.window-seconds=60"
})
@AutoConfigureMockMvc
class RateLimitingFilterIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectFourthRequestWithinWindow() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson("tx-rate-1").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson("tx-rate-2").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson("tx-rate-3").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson("tx-rate-4").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private String transactionJson(String transactionId) {
        return """
                {
                  "transactionId": "%s",
                  "accountId": "acc-rate-limit",
                  "amount": 100.00,
                  "currency": "COP",
                  "occurredAt": "2026-07-23T10:00:00Z",
                  "latitude": 4.7110,
                  "longitude": -74.0721,
                  "merchantId": "m-rate-limit",
                  "merchantCategory": "retail"
                }
                """.formatted(transactionId);
    }
}