package com.ingesta.dto;

import java.time.Instant;

public record TransactionResponse(
        String transactionId,
        String status,
        Instant ingestedAt
) {
}
