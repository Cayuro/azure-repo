package com.ingesta.repository;

import com.ingesta.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction> store = new ConcurrentHashMap<>();

    @Override
    public SaveOutcome saveIfAbsent(Transaction transaction) {
        Transaction previous = store.putIfAbsent(transaction.transactionId(), transaction);
        return previous == null ? SaveOutcome.CREATED : SaveOutcome.ALREADY_EXISTS;
    }

    @Override
    public Optional<Transaction> findById(String transactionId) {
        return Optional.ofNullable(store.get(transactionId));
    }
}
