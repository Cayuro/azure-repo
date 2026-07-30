package com.ingesta.repository.cosmos;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio Spring Data Cosmos para TransactionScoreEntity.
 * findByTransactionId es eficiente porque transactionId es la partition key.
 */
@Repository
public interface CosmosScoreSpringRepo
        extends CosmosRepository<TransactionScoreEntity, String> {

    Optional<TransactionScoreEntity> findByTransactionId(String transactionId);
}
