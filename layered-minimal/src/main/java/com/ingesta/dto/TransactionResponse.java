package com.ingesta.dto;

import java.time.Instant;

public class TransactionResponse {
    private final String transactionId;
    private final String status;
    private final Instant ingestedAt;

    public TransactionResponse(String transactionId, String status, Instant ingestedAt) {
        this.transactionId = transactionId;
        this.status = status;
        this.ingestedAt = ingestedAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
