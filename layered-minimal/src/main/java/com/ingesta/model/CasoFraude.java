package com.ingesta.model;

import java.time.Instant;
import java.util.List;

public class CasoFraude {
    private String caseId;
    private String transactionId;
    private int score;
    private int threshold;
    private Instant detectedAt;
    private List<String> reasons;

    public CasoFraude() {
    }

    public CasoFraude(String caseId, String transactionId, int score, int threshold,
                       Instant detectedAt, List<String> reasons) {
        this.caseId = caseId;
        this.transactionId = transactionId;
        this.score = score;
        this.threshold = threshold;
        this.detectedAt = detectedAt;
        this.reasons = reasons;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public int getScore() {
        return score;
    }

    public int getThreshold() {
        return threshold;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
