package com.ingesta.dto;

public class EvidenciaResponse {
    private final String transactionId;
    private final String blobName;

    public EvidenciaResponse(String transactionId, String blobName) {
        this.transactionId = transactionId;
        this.blobName = blobName;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getBlobName() {
        return blobName;
    }
}
