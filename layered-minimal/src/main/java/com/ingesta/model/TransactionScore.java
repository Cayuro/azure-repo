package com.ingesta.model;

import java.time.Instant;
import java.util.List;

public record TransactionScore(
        String transactionId,
        int score,
        int threshold,
        Instant scoredAt,
        List<RuleActivation> activations
) {
    public TransactionScore {
        activations = List.copyOf(activations);
    }
}