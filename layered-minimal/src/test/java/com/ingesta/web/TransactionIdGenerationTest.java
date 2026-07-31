package com.ingesta.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.config.RateLimitProperties;
import com.ingesta.controller.TransactionController;
import com.ingesta.dto.TransactionRequest;
import com.ingesta.messaging.TransactionEventPublisher;
import com.ingesta.repository.DatosDocumentoRepository;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.service.DocumentIntelligenceService;
import com.ingesta.service.EvidenciaService;
import com.ingesta.service.IngestaQueueEventPublisher;
import com.ingesta.service.TransactionScoringService;
import com.ingesta.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG 4: TransactionService generaba el transactionId con un AtomicLong en memoria
 * (TXN-1, TXN-2...). Tras un reinicio o con mas de una replica, el contador vuelve a
 * empezar en 1 y colisiona con ids ya usados por OTRA transaccion; la transaccion nueva
 * recibe 200 YA_RECIBIDA en vez de 202, y sus datos financieros se descartan en silencio.
 *
 * Mismo patron que TransactionOccurredAtValidationTest: se importa el TransactionService
 * real (no se mockea, a diferencia de WebLayerBootProbeTest) para ejercitar
 * generateTransactionId() de verdad a traves de dos ingestas consecutivas sin
 * transactionId explicito.
 */
@WebMvcTest(controllers = TransactionController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
@Import(TransactionService.class)
class TransactionIdGenerationTest {

    private static final Instant AHORA = Instant.parse("2026-07-30T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionRepository transactionRepository;
    @MockBean
    private TransactionEventPublisher transactionEventPublisher;
    @MockBean
    private IngestaQueueEventPublisher ingestaQueueEventPublisher;
    @MockBean
    private EvidenciaService evidenciaService;
    @MockBean
    private TransactionScoringService transactionScoringService;
    @MockBean
    private DocumentIntelligenceService documentIntelligenceService;
    @MockBean
    private DatosDocumentoRepository datosDocumentoRepository;

    @TestConfiguration
    static class RelojFijoConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void elRepositorioSiempreAceptaComoNueva() {
        // Simula que cada id generado es realmente nuevo para el repositorio (nunca
        // ALREADY_EXISTS), para poder aislar en este test unicamente la generacion del id.
        when(transactionRepository.saveIfAbsent(any())).thenReturn(TransactionRepository.SaveOutcome.CREATED);
    }

    private String payloadSinTransactionId(String accountId) throws Exception {
        TransactionRequest request = new TransactionRequest(
                null,
                accountId,
                new BigDecimal("100.00"),
                "USD",
                AHORA,
                4.7110,
                -74.0721,
                "mer-1",
                "retail"
        );
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void dosIngestasSinTransactionIdProducenIdentificadoresDistintos() throws Exception {
        MvcResult primero = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadSinTransactionId("acc-1")))
                .andExpect(status().isAccepted())
                .andReturn();

        MvcResult segundo = mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadSinTransactionId("acc-2")))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode primeraRespuesta = objectMapper.readTree(primero.getResponse().getContentAsString());
        JsonNode segundaRespuesta = objectMapper.readTree(segundo.getResponse().getContentAsString());

        String primerId = primeraRespuesta.get("transactionId").asText();
        String segundoId = segundaRespuesta.get("transactionId").asText();

        assertNotEquals(primerId, segundoId,
                "Dos transacciones sin id explicito no deben recibir el mismo transactionId generado");
        // Antes del fix ambos hubieran sido "TXN-1" (AtomicLong reiniciado por cada
        // contexto/replica); ahora deben llevar un UUID distinto en el sufijo.
        assertEquals("RECIBIDA", primeraRespuesta.get("status").asText());
        assertEquals("RECIBIDA", segundaRespuesta.get("status").asText());
    }
}
