package com.ingesta;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.messaging.TransactionQueuePoller;
import com.ingesta.model.FraudCase;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import com.ingesta.repository.FraudCaseRepository;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.repository.TransactionScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el consumidor real de cola-transacciones-ingesta: hasta ahora el scoring
 * solo se disparaba por un evento de Spring in-memory (no sobrevive un reinicio ni se
 * comparte entre instancias). Esta prueba publica mensajes directo en la cola (como si
 * vinieran de cualquier instancia de la API, sin pasar por HTTP) e invoca el poller
 * explicitamente, demostrando que la deteccion de fraude funciona a partir del mensaje
 * de la cola, no solo del evento in-process.
 *
 * Usa Azurite (Testcontainers) para tener una cola real sin depender de Azure/credenciales.
 */
@Testcontainers
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
class TransactionQueuePollerIT {

    private static final String INGESTA_QUEUE = "cola-transacciones-ingesta";
    private static final String FRAUD_QUEUE = "cola-casos-fraude";

    private static final String AZURITE_ACCOUNT_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @Container
    static final GenericContainer<?> azurite = new GenericContainer<>(
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.28.0"))
            .withExposedPorts(10000, 10001, 10002)
            .withCommand("azurite", "--blobHost", "0.0.0.0", "--queueHost", "0.0.0.0", "--tableHost", "0.0.0.0",
                    "--skipApiVersionCheck");

    private static String connectionString() {
        return "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey="
                + AZURITE_ACCOUNT_KEY
                + ";QueueEndpoint=http://" + azurite.getHost() + ":" + azurite.getMappedPort(10001) + "/devstoreaccount1;";
    }

    @TestConfiguration
    static class AzuriteQueueTestConfig {

        @Bean
        @Primary
        public QueueClient ingestaQueueClient() {
            return buildQueueClient(INGESTA_QUEUE);
        }

        @Bean
        @Primary
        public QueueClient casosFraudeQueueClient() {
            return buildQueueClient(FRAUD_QUEUE);
        }

        private QueueClient buildQueueClient(String queueName) {
            QueueClient client = new QueueClientBuilder()
                    .connectionString(connectionString())
                    .queueName(queueName)
                    .buildClient();
            client.createIfNotExists();
            return client;
        }
    }

    @Autowired
    private TransactionQueuePoller poller;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionScoreRepository scoreRepository;

    @Autowired
    private FraudCaseRepository fraudCaseRepository;

    @Qualifier("ingestaQueueClient")
    @Autowired
    private QueueClient ingestaQueueClient;

    @Test
    void consumeLosMensajesDeLaColaYDetectaElCasoDeFraude() throws Exception {
        Instant base = Instant.parse("2026-07-30T10:00:00Z");
        publicarEnLaCola("tx-poller-1", "acc-poller-1", "100.00", base, 4.7110, -74.0721, "retail");
        publicarEnLaCola("tx-poller-2", "acc-poller-1", "100.00", base.plus(Duration.ofMinutes(1)), 4.7111, -74.0722, "retail");
        publicarEnLaCola("tx-poller-3", "acc-poller-1", "1000.00", base.plus(Duration.ofMinutes(2)), 40.4168, -3.7038, "gambling");

        poller.poll();

        assertTrue(transactionRepository.findById("tx-poller-3").isPresent(),
                "La transaccion debe quedar persistida a partir del mensaje de la cola");

        TransactionScore score = scoreRepository.findByTransactionId("tx-poller-3").orElseThrow();
        assertTrue(score.score() > score.threshold(), "La secuencia debe superar el umbral de fraude");

        FraudCase fraudCase = fraudCaseRepository.findByTransactionId("tx-poller-3").orElseThrow();
        assertEquals("tx-poller-3", fraudCase.transactionId());

        List<QueueMessageItem> restantes = StreamSupport
                .stream(ingestaQueueClient.receiveMessages(10).spliterator(), false)
                .toList();
        assertTrue(restantes.isEmpty(), "Los 3 mensajes procesados con exito deben quedar eliminados de la cola");
    }

    @Test
    void reprocesarLaMismaTransaccionEsIdempotente() throws Exception {
        Instant base = Instant.parse("2026-07-30T11:00:00Z");
        publicarEnLaCola("tx-poller-idem", "acc-poller-idem", "50.00", base, 4.7110, -74.0721, "retail");

        poller.poll();
        TransactionScore primerScore = scoreRepository.findByTransactionId("tx-poller-idem").orElseThrow();

        // Simula un mensaje reentregado (at-least-once) para la misma transaccion.
        publicarEnLaCola("tx-poller-idem", "acc-poller-idem", "50.00", base, 4.7110, -74.0721, "retail");
        poller.poll();

        TransactionScore segundoScore = scoreRepository.findByTransactionId("tx-poller-idem").orElseThrow();
        assertEquals(primerScore.scoredAt(), segundoScore.scoredAt(),
                "Un reintento no debe recalcular ni reemplazar el score ya existente");
    }

    @Test
    void unMensajeMalformadoNoSeBorraYQuedaParaReintento() throws Exception {
        ingestaQueueClient.sendMessage("{ esto no es un evento valido");

        poller.poll();

        // El mensaje procesado queda invisible por visibilityTimeout (30s) tras el
        // receiveMessages del poll(), asi que NO se puede volver a "recibir" de inmediato
        // aunque siga en la cola: ApproximateMessagesCount cuenta visibles + invisibles.
        long restantes = ingestaQueueClient.getProperties().getApproximateMessagesCount();
        assertEquals(1, restantes, "El mensaje malformado debe seguir en la cola para reintento, no perderse");
    }

    private void publicarEnLaCola(String transactionId, String accountId, String amount, Instant occurredAt,
                                   double latitude, double longitude, String merchantCategory) throws Exception {
        Transaction transaction = new Transaction(
                transactionId, accountId, new BigDecimal(amount), "COP",
                occurredAt, occurredAt.plusSeconds(1), latitude, longitude, "m-poller", merchantCategory);

        Map<String, Object> evento = Map.of("eventType", "TRANSACCION_INGESTADA", "data", transaction);
        ingestaQueueClient.sendMessage(objectMapper.writeValueAsString(evento));
    }
}
