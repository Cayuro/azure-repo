package com.ingesta.repository;

import com.ingesta.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

    @Override
    public List<Transaction> findByAccountId(String accountId) {
        return store.values().stream()
                .filter(transaction -> transaction.accountId().equals(accountId))
                .sorted((left, right) -> left.ingestedAt().compareTo(right.ingestedAt()))
                .collect(Collectors.toUnmodifiableList());
    }
}
