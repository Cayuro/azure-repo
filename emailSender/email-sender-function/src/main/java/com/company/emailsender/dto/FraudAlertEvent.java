package com.company.emailsender.dto;

import com.company.emailsender.model.RuleActivation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FraudAlertEvent(
    String transactionId,
    String accountId,
    BigDecimal amount,
    String currency,
    Instant occurredAt,
    Instant ingestedAt,
    Double latitude,
    Double longitude,
    String merchantId,
    String merchantCategory,
    int score,
    int threshold,
    Instant scoredAt,
    List<RuleActivation> activations
) {}
