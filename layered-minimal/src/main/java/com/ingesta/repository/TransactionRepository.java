package com.ingesta.repository;

import com.ingesta.model.Transaction;

import java.util.Optional;

public interface TransactionRepository {

    SaveOutcome saveIfAbsent(Transaction transaction);

    Optional<Transaction> findById(String transactionId);

    enum SaveOutcome {
        CREATED,
        ALREADY_EXISTS
    }
}
