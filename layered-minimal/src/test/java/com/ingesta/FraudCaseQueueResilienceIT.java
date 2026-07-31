package com.ingesta;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica la HU: con el consumidor de casos detenido (aqui, simplemente no hay ningun
 * consumidor leyendo la cola durante la primera fase), la API sigue recibiendo/respondiendo
 * transacciones con normalidad; al "restablecer el consumidor" (la propia prueba lee la cola
 * al final, como lo haria el equipo analitico), todos los casos deben aparecer sin perdidas.
 *
 * Usa Azurite (emulador local de Azure Storage via Testcontainers) para tener una cola real
 * sin depender de credenciales de Azure.
 */
@Testcontainers
// El perfil por defecto de application.properties es "prod", que activa Cosmos y el
// repositorio JPA contra la infraestructura real. Los tests se fijan a "local" para
// quedarse con los repositorios en memoria y no tocar Azure (VULN 4).
@ActiveProfiles("local")
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
class FraudCaseQueueResilienceIT {

    private static final String INGESTA_QUEUE = "cola-transacciones-ingesta";
    private static final String FRAUD_QUEUE = "cola-casos-fraude";

    // Cuenta de desarrollo bien conocida de Azurite (no es un secreto real, es publica
    // y la misma para cualquier instancia local del emulador).
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

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Qualifier("casosFraudeQueueClient")
    @org.springframework.beans.factory.annotation.Autowired
    private QueueClient casosFraudeQueueClient;

    @Test
    void apiSigueRespondiendoConConsumidorCaidoYNingunCasoSePierdeAlRestablecerlo() throws Exception {
        // FASE 1: el consumidor de casos esta "detenido" -- de hecho, nadie lee cola-casos-fraude
        // en absoluto durante esta fase, tal como pasa hoy en produccion (el consumidor es un
        // sistema externo que aun no existe). Aun asi, la API debe responder con normalidad.
        enviarSecuenciaQueDisparaFraude("acc-resil-1", "tx-resil-1");
        enviarSecuenciaQueDisparaFraude("acc-resil-2", "tx-resil-2");

        // Le damos tiempo al flujo async (evento in-process -> scoring -> publicacion a la cola)
        // a que termine, sin que la API haya esperado nada de esto para responder.
        Thread.sleep(1500);

        // FASE 2: "se restablece el consumidor" -- la prueba misma lee la cola, como lo haria
        // el equipo analitico, y verifica que ambos casos siguen ahi, sin perdidas.
        List<QueueMessageItem> mensajes = StreamSupport
                .stream(casosFraudeQueueClient.receiveMessages(10).spliterator(), false)
                .collect(Collectors.toList());

        assertEquals(2, mensajes.size(), "Deberian llegar exactamente los 2 casos de fraude generados, sin perdidas");

        Set<String> transactionIdsEnLaCola = mensajes.stream()
                .map(this::extraerTransactionId)
                .collect(Collectors.toSet());

        assertEquals(Set.of("tx-resil-1-3", "tx-resil-2-3"), transactionIdsEnLaCola);
    }

    private String extraerTransactionId(QueueMessageItem mensaje) throws RuntimeException {
        try {
            JsonNode raiz = objectMapper.readTree(mensaje.getMessageText());
            assertEquals("CASO_FRAUDE_DETECTADO", raiz.get("eventType").asText());
            return raiz.get("data").get("transactionId").asText();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Reproduce la misma secuencia de TransactionScoringIT (velocidad + monto atipico +
     * geo imposible + comercio de riesgo) para disparar un caso de fraude sobre una cuenta
     * dada, cuyo transactionId final es "{prefix}-3".
     */
    private void enviarSecuenciaQueDisparaFraude(String accountId, String transactionIdPrefix) throws Exception {
        Instant base = Instant.parse("2026-07-28T10:00:00Z");

        postTransaccion(transactionIdPrefix + "-1", accountId, "100.00", base, 4.7110, -74.0721, "retail");
        postTransaccion(transactionIdPrefix + "-2", accountId, "100.00", base.plus(Duration.ofMinutes(1)), 4.7111, -74.0722, "retail");
        postTransaccion(transactionIdPrefix + "-3", accountId, "1000.00", base.plus(Duration.ofMinutes(2)), 40.4168, -3.7038, "gambling");
    }

    private void postTransaccion(String transactionId, String accountId, String amount, Instant occurredAt,
                                  double latitude, double longitude, String merchantCategory) throws Exception {
        String payload = """
                {
                  "transactionId": "%s",
                  "accountId": "%s",
                  "amount": %s,
                  "currency": "COP",
                  "occurredAt": "%s",
                  "latitude": %s,
                  "longitude": %s,
                  "merchantId": "m-resil",
                  "merchantCategory": "%s"
                }
                """.formatted(transactionId, accountId, amount, occurredAt, latitude, longitude, merchantCategory);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECIBIDA"));
    }
}
