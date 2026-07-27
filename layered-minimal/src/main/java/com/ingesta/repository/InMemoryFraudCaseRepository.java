package com.ingesta.repository;

import com.ingesta.model.FraudCase;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryFraudCaseRepository implements FraudCaseRepository {

    private final Map<String, FraudCase> store = new ConcurrentHashMap<>();

    @Override
    public void save(FraudCase fraudCase) {
        store.put(fraudCase.transactionId(), fraudCase);
    }

    @Override
    public Optional<FraudCase> findByTransactionId(String transactionId) {
        return Optional.ofNullable(store.get(transactionId));
    }
}