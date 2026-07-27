package com.ingesta.model;

import java.time.Instant;
import java.util.List;

public record FraudCase(
        String caseId,
        String transactionId,
        int score,
        String status,
        Instant openedAt,
        List<RuleActivation> activations
) {
    public FraudCase {
        activations = List.copyOf(activations);
    }
}