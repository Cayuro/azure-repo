package com.ingesta.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Transaction {
    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private Instant occurredAt;
    private Instant ingestedAt;
    private Double latitude;
    private Double longitude;
    private String merchantId;
    private String merchantCategory;

    public Transaction() {
    }

    public Transaction(String transactionId, String accountId, BigDecimal amount, String currency,
                       Instant occurredAt, Instant ingestedAt, Double latitude, Double longitude,
                       String merchantId, String merchantCategory) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.occurredAt = occurredAt;
        this.ingestedAt = ingestedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.merchantId = merchantId;
        this.merchantCategory = merchantCategory;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }
}
