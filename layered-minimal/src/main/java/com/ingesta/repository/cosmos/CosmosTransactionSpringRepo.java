package com.ingesta.repository.cosmos;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio Spring Data Cosmos para TransactionEntity.
 * El framework genera la implementación en tiempo de ejecución.
 * findByAccountId es eficiente porque accountId es la partition key del contenedor.
 */
@Repository
public interface CosmosTransactionSpringRepo
        extends CosmosRepository<TransactionEntity, String> {

    List<TransactionEntity> findByAccountId(String accountId);
}
