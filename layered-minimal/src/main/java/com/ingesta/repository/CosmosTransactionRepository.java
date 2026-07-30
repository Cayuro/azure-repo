package com.ingesta.repository;

import com.ingesta.model.Transaction;
import com.ingesta.repository.cosmos.CosmosTransactionSpringRepo;
import com.ingesta.repository.cosmos.TransactionEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Implementación de TransactionRepository respaldada por Azure Cosmos DB.
 *
 * @Primary indica a Spring que use este bean en lugar de InMemoryTransactionRepository
 * cuando se inyecta la interfaz TransactionRepository.
 */
@Primary
@Repository
public class CosmosTransactionRepository implements TransactionRepository {

    private final CosmosTransactionSpringRepo springRepo;

    public CosmosTransactionRepository(CosmosTransactionSpringRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public SaveOutcome saveIfAbsent(Transaction transaction) {
        if (springRepo.existsById(transaction.transactionId())) {
            return SaveOutcome.ALREADY_EXISTS;
        }
        springRepo.save(TransactionEntity.from(transaction));
        return SaveOutcome.CREATED;
    }

    @Override
    public Optional<Transaction> findById(String transactionId) {
        return springRepo.findById(transactionId)
                .map(TransactionEntity::toDomain);
    }

    @Override
    public List<Transaction> findAll() {
        // CosmosRepository.findAll() retorna Iterable — se convierte con StreamSupport
        return StreamSupport.stream(springRepo.findAll().spliterator(), false)
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public List<Transaction> findByAccountId(String accountId) {
        return springRepo.findByAccountId(accountId).stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }
}
