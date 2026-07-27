package com.ingesta.repository;

import com.ingesta.model.TransactionScore;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTransactionScoreRepository implements TransactionScoreRepository {

    private final Map<String, TransactionScore> store = new ConcurrentHashMap<>();

    @Override
    public void save(TransactionScore score) {
        store.put(score.transactionId(), score);
    }

    @Override
    public Optional<TransactionScore> findByTransactionId(String transactionId) {
        return Optional.ofNullable(store.get(transactionId));
    }
}