package com.ingesta.repository;

import com.ingesta.model.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    SaveOutcome saveIfAbsent(Transaction transaction);

    Optional<Transaction> findById(String transactionId);

    List<Transaction> findByAccountId(String accountId);

    enum SaveOutcome {
        CREATED,
        ALREADY_EXISTS
    }
}
