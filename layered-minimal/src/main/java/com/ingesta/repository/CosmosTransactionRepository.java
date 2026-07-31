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
 *
 * VULN 4 (ALTO): esta clase importaba ConditionalOnProperty pero nunca lo aplicaba (import
 * muerto de una condicion perdida en un refactor). Sin ella, @Primary @Repository activaba
 * este bean SIEMPRE, incluso en tests: su constructor exige un CosmosTransactionSpringRepo
 * real, y application.properties apunta sin condicion a la cuenta de PRODUCCION
 * (spring.cloud.azure.cosmos.endpoint=cosmos-centinela-prod). Cualquier @SpringBootTest
 * terminaba intentando hablar con produccion sin credenciales.
 *
 * FIX: se reutiliza como interruptor "spring.cloud.azure.cosmos.enabled", la MISMA
 * propiedad que ya gobierna, en el auto-configure de Spring Cloud Azure, la creacion del
 * CosmosClient y de los repositorios Spring Data Cosmos (AzureCosmosAutoConfiguration /
 * CosmosDataAutoConfiguration / CosmosRepositoriesAutoConfiguration; verificado leyendo su
 * bytecode: @ConditionalOnProperty("spring.cloud.azure.cosmos.enabled", matchIfMissing=true)
 * y @ConditionalOnExpression("${spring.cloud.azure.cosmos.enabled:true}"). No basta con
 * condicionar solo este wrapper: si el framework igual construyera el CosmosClient, el
 * problema seguiria. Al compartir la misma propiedad, cuando esta deshabilitada NINGUNA
 * pieza de Cosmos (ni el cliente, ni CosmosTransactionSpringRepo, ni este wrapper) se
 * activa, y el unico TransactionRepository disponible es InMemoryTransactionRepository
 * (siempre registrado, sin condicion).
 *
 * COMO ACTIVAR CADA MODO:
 * - Por defecto (tests, `mvn test`, desarrollo local sin credenciales de Azure): la
 *   propiedad NO esta definida -> matchIfMissing=false aqui (a proposito, al reves que el
 *   default del framework) -> se usa InMemoryTransactionRepository.
 * - Produccion (Azure App Service / Container Apps): definir la variable de entorno
 *   INGESTA_COSMOS_ENABLED=true (ver application.properties), igual que ya se hace con
 *   SPRING_DATASOURCE_*. Con eso spring.cloud.azure.cosmos.enabled=true y se activa el
 *   CosmosClient real + este repositorio (autenticado por Identidad Gestionada).
 */
@Primary
@Repository
@ConditionalOnProperty(prefix = "spring.cloud.azure.cosmos", name = "enabled", havingValue = "true")
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
