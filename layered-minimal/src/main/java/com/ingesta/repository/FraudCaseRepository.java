package com.ingesta.repository;

import com.ingesta.model.FraudCase;

import java.util.Optional;

public interface FraudCaseRepository {

    void save(FraudCase fraudCase);

    Optional<FraudCase> findByTransactionId(String transactionId);
}