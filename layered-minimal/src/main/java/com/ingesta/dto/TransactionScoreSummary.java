package com.ingesta.dto;

public record TransactionScoreSummary(
        String transactionId,
        int score,
        int threshold,
        boolean scored
) {
}
