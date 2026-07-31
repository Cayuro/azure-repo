package com.ingesta.repository;

import com.ingesta.repository.cosmos.CosmosScoreSpringRepo;
import com.ingesta.repository.cosmos.CosmosTransactionSpringRepo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * VULN 4 (ALTO): CosmosTransactionRepository y CosmosTransactionScoreRepository eran
 * @Primary @Repository SIN condicion (el import de ConditionalOnProperty estaba muerto).
 * Esto significaba que se activaban SIEMPRE, incluso al levantar un contexto de test sin
 * ninguna credencial de Azure, intentando hablar con la cuenta real cosmos-centinela-prod.
 *
 * Esta prueba NO levanta el contexto completo de la aplicacion (evita exactamente el
 * problema que se esta corrigiendo): registra solo las cuatro clases de repositorio
 * involucradas mas un stub de los repositorios Spring Data Cosmos (para que el
 * constructor de los wrappers de Cosmos tenga algo que inyectar SI la condicion los
 * llega a activar) y verifica el efecto de @ConditionalOnProperty en ambos sentidos.
 */
class CosmosRepositoryActivationConditionTest {

    @Configuration
    static class SpringDataCosmosRepoStubs {
        @Bean
        CosmosTransactionSpringRepo cosmosTransactionSpringRepo() {
            return mock(CosmosTransactionSpringRepo.class);
        }

        @Bean
        CosmosScoreSpringRepo cosmosScoreSpringRepo() {
            return mock(CosmosScoreSpringRepo.class);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SpringDataCosmosRepoStubs.class,
                    CosmosTransactionRepository.class,
                    CosmosTransactionScoreRepository.class,
                    InMemoryTransactionRepository.class,
                    InMemoryTransactionScoreRepository.class);

    @Test
    void sinConfiguracionDeCosmosSeUsanLosRepositoriosEnMemoria() {
        // Caso por defecto: ni en `mvn test` ni en un @SpringBootTest sin
        // spring.cloud.azure.cosmos.enabled=true deben existir los beans de Cosmos.
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CosmosTransactionRepository.class);
            assertThat(context).doesNotHaveBean(CosmosTransactionScoreRepository.class);
            assertThat(context.getBean(TransactionRepository.class))
                    .isInstanceOf(InMemoryTransactionRepository.class);
            assertThat(context.getBean(TransactionScoreRepository.class))
                    .isInstanceOf(InMemoryTransactionScoreRepository.class);
        });
    }

    @Test
    void conSpringCloudAzureCosmosEnabledSeActivanLosRepositoriosDeCosmos() {
        // Modo produccion (INGESTA_COSMOS_ENABLED=true -> spring.cloud.azure.cosmos.enabled=true):
        // los repositorios de Cosmos existen y @Primary los prefiere sobre los in-memory.
        contextRunner.withPropertyValues("spring.cloud.azure.cosmos.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CosmosTransactionRepository.class);
                    assertThat(context).hasSingleBean(CosmosTransactionScoreRepository.class);
                    assertThat(context.getBean(TransactionRepository.class))
                            .isInstanceOf(CosmosTransactionRepository.class);
                    assertThat(context.getBean(TransactionScoreRepository.class))
                            .isInstanceOf(CosmosTransactionScoreRepository.class);
                });
    }
}
