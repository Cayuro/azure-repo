package com.ingesta.repository;

import com.ingesta.model.TransactionScore;

import java.util.Optional;

public interface TransactionScoreRepository {

    void save(TransactionScore score);

    Optional<TransactionScore> findByTransactionId(String transactionId);
}