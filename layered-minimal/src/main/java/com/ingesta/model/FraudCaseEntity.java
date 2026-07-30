package com.ingesta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "cases")
public class FraudCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_case")
    private Integer idCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_status", nullable = false)
    private StatusEntity status;

    @Column(name = "id_transaction", nullable = false, unique = true, length = 255)
    private String transactionId;

    @Column(name = "score")
    private Integer score;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    protected FraudCaseEntity() {
        // JPA
    }

    public FraudCaseEntity(StatusEntity status, String transactionId, Integer score, Instant openedAt) {
        this.status = status;
        this.transactionId = transactionId;
        this.score = score;
        this.openedAt = openedAt;
    }

    public Integer getIdCase() {
        return idCase;
    }

    public StatusEntity getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Integer getScore() {
        return score;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setStatus(StatusEntity status) {
        this.status = status;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }
}
