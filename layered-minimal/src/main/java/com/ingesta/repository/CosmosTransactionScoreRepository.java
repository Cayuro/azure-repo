package com.ingesta.repository;

import com.ingesta.model.TransactionScore;
import com.ingesta.repository.cosmos.CosmosScoreSpringRepo;
import com.ingesta.repository.cosmos.TransactionScoreEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Implementación de TransactionScoreRepository respaldada por Azure Cosmos DB.
 *
 * @Primary indica a Spring que use este bean en lugar de InMemoryTransactionScoreRepository.
 */
@Primary
@Repository
public class CosmosTransactionScoreRepository implements TransactionScoreRepository {

    private final CosmosScoreSpringRepo springRepo;

    public CosmosTransactionScoreRepository(CosmosScoreSpringRepo springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(TransactionScore score) {
        springRepo.save(TransactionScoreEntity.from(score));
    }

    @Override
    public Optional<TransactionScore> findByTransactionId(String transactionId) {
        return springRepo.findByTransactionId(transactionId)
                .map(TransactionScoreEntity::toDomain);
    }

    @Override
    public List<TransactionScore> findAll() {
        return StreamSupport.stream(springRepo.findAll().spliterator(), false)
                .map(TransactionScoreEntity::toDomain)
                .toList();
    }
}
