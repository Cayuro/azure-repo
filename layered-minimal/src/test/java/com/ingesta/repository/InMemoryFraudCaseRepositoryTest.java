package com.ingesta.repository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.ingesta.model.FraudCase;

class InMemoryFraudCaseRepositoryTest {

    @Test
    void saveAndFindByTransactionIdReturnsPersistedFraudCase() {
        InMemoryFraudCaseRepository repository = new InMemoryFraudCaseRepository();
        FraudCase fraudCase = new FraudCase(
                "case-1",
                "TXN-3",
                85,
                "OPEN",
                Instant.parse("2024-01-01T00:00:00Z"),
                List.of()
        );

        repository.save(fraudCase);

        var stored = repository.findByTransactionId("TXN-3");
        assertTrue(stored.isPresent());
        assertEquals("TXN-3", stored.get().transactionId());
        assertEquals(85, stored.get().score());
    }
}
