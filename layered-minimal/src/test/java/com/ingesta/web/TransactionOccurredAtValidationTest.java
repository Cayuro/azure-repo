package com.ingesta.web;

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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG 2: TransactionService.validate() construia una lista de errores que nunca se
 * llenaba (el if (!errors.isEmpty()) era inalcanzable), asi que una transaccion con
 * occurredAt en el futuro (p.ej. anio 2999) se aceptaba con 202 y contaminaba el motor
 * de scoring con datos imposibles.
 *
 * Se usa el patron @WebMvcTest de WebLayerBootProbeTest, pero en vez de mockear
 * TransactionService (como hace ese archivo), se importa la clase real con @Import y se
 * mockean unicamente SUS colaboradores (repository, publishers) mas un Clock fijo, para
 * ejercitar la logica real de validate() a traves del endpoint HTTP.
 */
@WebMvcTest(controllers = TransactionController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
@Import(TransactionService.class)
class TransactionOccurredAtValidationTest {

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
    void aceptarCualquierTransaccionEnElRepositorio() {
        when(transactionRepository.saveIfAbsent(any())).thenReturn(TransactionRepository.SaveOutcome.CREATED);
    }

    private TransactionRequest requestConOccurredAt(Instant occurredAt) {
        return new TransactionRequest(
                null,
                "acc-1",
                new BigDecimal("100.00"),
                "USD",
                occurredAt,
                4.7110,
                -74.0721,
                "mer-1",
                "retail"
        );
    }

    @Test
    void fechaPasadaEsAceptada() throws Exception {
        String payload = objectMapper.writeValueAsString(requestConOccurredAt(AHORA.minusSeconds(3600)));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECIBIDA"));
    }

    @Test
    void fechaPresenteEsAceptada() throws Exception {
        // occurredAt == "ahora" (segun el Clock inyectado) no es estrictamente posterior,
        // asi que no debe rechazarse.
        String payload = objectMapper.writeValueAsString(requestConOccurredAt(AHORA));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECIBIDA"));
    }

    @Test
    void fechaFuturaEsRechazada() throws Exception {
        // Caso del reporte de bugs: una transaccion fechada en el 2999.
        Instant anio2999 = Instant.parse("2999-01-01T00:00:00Z");
        String payload = objectMapper.writeValueAsString(requestConOccurredAt(anio2999));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La transaccion no cumple el contrato"))
                .andExpect(jsonPath("$.details[0]").value("occurredAt: no puede ser una fecha futura"));
    }

    @Test
    void unSegundoEnElFuturoEsRechazado() throws Exception {
        // Caso frontera: basta con un segundo por delante del reloj inyectado para rechazar.
        String payload = objectMapper.writeValueAsString(requestConOccurredAt(AHORA.plusSeconds(1)));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("occurredAt: no puede ser una fecha futura"));
    }
}
