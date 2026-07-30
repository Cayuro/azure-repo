package com.ingesta.repository;

import com.ingesta.model.FraudCase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("local")
public class InMemoryFraudCaseRepository implements FraudCaseRepository {

    // Mapa local para pruebas en localhost. Se deja comentado si se desea evitar memoria local.
    // private final Map<String, FraudCase> store = new ConcurrentHashMap<>();

    @Override
    public void save(FraudCase fraudCase) {
        // store.put(fraudCase.transactionId(), fraudCase);
    }

    @Override
    public Optional<FraudCase> findByTransactionId(String transactionId) {
        return Optional.empty();
    }
}