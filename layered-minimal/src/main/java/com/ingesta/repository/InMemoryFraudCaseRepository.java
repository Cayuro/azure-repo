package com.ingesta.repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.ingesta.model.FraudCase;

@Repository
@Profile("local")
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