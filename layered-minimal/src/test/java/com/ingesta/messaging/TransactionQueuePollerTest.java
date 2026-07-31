package com.ingesta.messaging;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.util.BinaryData;
import com.azure.core.util.IterableStream;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ingesta.dto.CasoFraudeDetectadoEvento;
import com.ingesta.dto.DocumentoProcesadoEvento;
import com.ingesta.dto.TransaccionIngestadaEvento;
import com.ingesta.model.DatosDocumento;
import com.ingesta.model.FraudCase;
import com.ingesta.model.Transaction;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.service.TransactionScoringService;
import com.ingesta.testsupport.TransaccionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias puras (Mockito, sin Spring, sin credenciales de Azure) de
 * TransactionQueuePoller. En cada caso se verifican SIEMPRE dos cosas: que se
 * persiste/puntua lo correcto (o que NO se toca cuando no corresponde) y si el mensaje
 * SE BORRA o NO de la cola original -- esa combinacion es la garantia real de no perder
 * casos de fraude ni envenenar la cola.
 *
 * Cubre:
 * - BUG 1: cola-transacciones-ingesta es compartida; el poller debe leer eventType y
 *   solo tratar como transaccion los mensajes TRANSACCION_INGESTADA. Los eventType
 *   conocidos pero ajenos (DOCUMENTO_PROCESADO, CASO_FRAUDE_DETECTADO) y los
 *   desconocidos deben borrarse, nunca quedar en bucle.
 * - BUG 4: el envelope y la Transaction deserializada se validan explicitamente;
 *   invalidos son PERMANENTES (se descartan sin reintentar).
 * - BUG 5: los fallos transitorios se reintentan hasta un limite configurable; al
 *   superarlo, el mensaje se mueve a la cola de descartes y se borra del original.
 */
class TransactionQueuePollerTest {

    private static final String MESSAGE_ID = "msg-1";
    private static final String POP_RECEIPT = "pop-1";

    private QueueClient queueClient;
    private QueueClient poisonQueueClient;
    private ObjectMapper objectMapper;
    private TransactionScoringService scoringService;
    private TransactionRepository transactionRepository;
    private TransactionQueuePoller poller;

    @BeforeEach
    void prepararPoller() {
        queueClient = mock(QueueClient.class);
        poisonQueueClient = mock(QueueClient.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        scoringService = mock(TransactionScoringService.class);
        transactionRepository = mock(TransactionRepository.class);

        poller = new TransactionQueuePoller(queueClient, poisonQueueClient, objectMapper, scoringService, transactionRepository);

        // Los campos @Value no los rellena Spring en un test unitario puro; se fijan a
        // mano para que el limite de reintentos (BUG 5) sea determinista en las pruebas.
        ReflectionTestUtils.setField(poller, "maxMessages", 5);
        ReflectionTestUtils.setField(poller, "visibilityTimeoutSeconds", 30);
        ReflectionTestUtils.setField(poller, "maxRetries", 3);

        when(queueClient.receiveMessages(anyInt(), any(Duration.class), any(), any()))
                .thenAnswer(invocation -> paginaVacia());
    }

    private QueueMessageItem mensaje(String body, long dequeueCount) {
        return new QueueMessageItem()
                .setMessageId(MESSAGE_ID)
                .setPopReceipt(POP_RECEIPT)
                .setBody(BinaryData.fromString(body))
                .setDequeueCount(dequeueCount);
    }

    /**
     * Fabrica un PagedIterable REAL (no un mock) de una sola pagina sin continuacion.
     * Mockear PagedIterable directamente (mock(PagedIterable.class)) dispara el metodo
     * real stream() heredado -- que Mockito no puede interceptar de forma consistente y
     * termina en UnfinishedStubbingException -- asi que en su lugar se construye con el
     * constructor que recibe un Supplier<PagedResponse<T>>, exactamente el mismo shape
     * de una sola pagina que devuelve receiveMessages en la practica.
     */
    private PagedIterable<QueueMessageItem> paginaCon(QueueMessageItem... mensajes) {
        PagedResponse<QueueMessageItem> respuesta = new PagedResponse<>() {
            @Override
            public IterableStream<QueueMessageItem> getElements() {
                return IterableStream.of(List.of(mensajes));
            }

            @Override
            public String getContinuationToken() {
                return null;
            }

            @Override
            public int getStatusCode() {
                return 200;
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            @Override
            public HttpRequest getRequest() {
                return null;
            }

            @Override
            public void close() {
                // no-op: no hay recursos reales que liberar en este doble de prueba.
            }
        };
        return new PagedIterable<>(() -> respuesta);
    }

    private PagedIterable<QueueMessageItem> paginaVacia() {
        return paginaCon();
    }

    private void llegaALaCola(QueueMessageItem mensaje) {
        when(queueClient.receiveMessages(anyInt(), any(Duration.class), any(), any()))
                .thenReturn(paginaCon(mensaje));
    }

    private String envelopeTransaccionIngestada(Transaction transaccion) throws Exception {
        return objectMapper.writeValueAsString(new TransaccionIngestadaEvento(transaccion));
    }

    // ---------------------------------------------------------------
    // BUG 1: enrutamiento por eventType
    // ---------------------------------------------------------------

    @Test
    void unaTransaccionIngestadaValidaSeProcesaYElMensajeSeBorra() throws Exception {
        Transaction transaccion = TransaccionFixture.una().conId("tx-1").construir();
        llegaALaCola(mensaje(envelopeTransaccionIngestada(transaccion), 1));

        poller.poll();

        // Se compara por valor (compareTo), no por equals: el viaje de ida y vuelta por
        // JSON (readTree produce un DoubleNode salvo que se habilite
        // USE_BIG_DECIMAL_FOR_FLOATS) puede normalizar la escala del BigDecimal
        // (100.00 -> 100.0) sin cambiar el valor monetario real ni afectar al scoring,
        // que tambien compara montos con compareTo.
        verify(transactionRepository, times(1)).saveIfAbsent(argThat(t ->
                t.transactionId().equals("tx-1") && t.amount().compareTo(transaccion.amount()) == 0));
        verify(scoringService, times(1)).procesarTransaccion(argThat(t ->
                t.transactionId().equals("tx-1") && t.amount().compareTo(transaccion.amount()) == 0));
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, never()).sendMessage(any(String.class));
    }

    @Test
    void unEventoDocumentoProcesadoSeBorraSinTocarElScoring() throws Exception {
        // Este es el bug critico: antes de la correccion, el poller ignoraba eventType
        // e intentaba deserializar CUALQUIER mensaje como Transaction. Un
        // DOCUMENTO_PROCESADO fallaba esa deserializacion y quedaba envenenando la cola
        // para siempre (30s en 30s). Ahora debe descartarse limpio, sin error.
        DatosDocumento datos = DatosDocumento.completado(
                "tx-1", "evidencia.pdf", "Juan Perez", "12345678",
                Map.of(), Instant.parse("2026-07-30T10:00:00Z"));
        String envelope = objectMapper.writeValueAsString(new DocumentoProcesadoEvento(datos));
        llegaALaCola(mensaje(envelope, 1));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
        verify(transactionRepository, never()).saveIfAbsent(any());
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        // No es un error, es trafico esperado de otro publicador: no va a la cola de descartes.
        verify(poisonQueueClient, never()).sendMessage(any(String.class));
    }

    @Test
    void unEventoCasoFraudeDetectadoSeBorraSinTocarElScoring() throws Exception {
        FraudCase caso = new FraudCase("case-1", "tx-1", 90, "ABIERTO", Instant.parse("2026-07-30T10:00:00Z"), List.of());
        String envelope = objectMapper.writeValueAsString(new CasoFraudeDetectadoEvento(caso));
        llegaALaCola(mensaje(envelope, 1));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, never()).sendMessage(any(String.class));
    }

    @Test
    void unEventTypeDesconocidoSeBorraYSeMandaAPoisonParaNoQuedarEnBucle() {
        String envelope = """
                {"eventType":"ALGO_QUE_NO_EXISTE","data":{"cualquierCosa":true}}
                """;
        llegaALaCola(mensaje(envelope, 1));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, times(1)).sendMessage(envelope);
    }

    // ---------------------------------------------------------------
    // BUG 4: validacion explicita del envelope y de la Transaction
    // ---------------------------------------------------------------

    @Test
    void unMensajeQueNoEsJsonEsPermanenteYSeMandaAPoison() {
        String cuerpoInvalido = "esto no es json";
        llegaALaCola(mensaje(cuerpoInvalido, 1));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, times(1)).sendMessage(cuerpoInvalido);
    }

    @Test
    void unEnvelopeSinEventTypeEsPermanenteYSeMandaAPoison() {
        String envelope = """
                {"data":{"transactionId":"tx-1"}}
                """;
        llegaALaCola(mensaje(envelope, 1));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, times(1)).sendMessage(envelope);
    }

    @Test
    void unEnvelopeTransaccionIngestadaSinDataEsPermanenteYSeMandaAPoison() {
        // Antes de la correccion, raiz.get("data") devolvia null y el NPE opaco
        // resultante se registraba como si fuera un fallo transitorio cualquiera.
        String envelope = """
                {"eventType":"TRANSACCION_INGESTADA"}
                """;
        llegaALaCola(mensaje(envelope, 1));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, times(1)).sendMessage(envelope);
    }

    @Test
    void unaTransaccionConCamposNulosEsPermanenteYNuncaLlegaAlMotorDeScoring() {
        // amount, latitude, longitude y merchantCategory nulos: el camino HTTP los
        // rechaza con @Valid, pero el camino de cola no pasaba por ahi y esto
        // terminaba en NullPointerException dentro de TransactionScoringEngine.
        String envelope = """
                {"eventType":"TRANSACCION_INGESTADA","data":{
                    "transactionId":"tx-1",
                    "accountId":"acc-1",
                    "amount":null,
                    "currency":"USD",
                    "occurredAt":"2026-07-30T10:00:00Z",
                    "ingestedAt":"2026-07-30T10:00:00Z",
                    "latitude":null,
                    "longitude":null,
                    "merchantId":"mer-1",
                    "merchantCategory":null
                }}
                """;
        llegaALaCola(mensaje(envelope, 1));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
        verify(transactionRepository, never()).saveIfAbsent(any());
        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, times(1)).sendMessage(envelope);
    }

    // ---------------------------------------------------------------
    // BUG 5: limite de reintentos configurable
    // ---------------------------------------------------------------

    @Test
    void unFalloTransitorioPorDebajoDelLimiteDejaElMensajeSinBorrarParaReintentar() throws Exception {
        Transaction transaccion = TransaccionFixture.una().conId("tx-1").construir();
        llegaALaCola(mensaje(envelopeTransaccionIngestada(transaccion), 1)); // dequeueCount=1 < maxRetries=3

        doThrow(new RuntimeException("Cosmos no disponible")).when(scoringService).procesarTransaccion(any());

        poller.poll();

        // No se borra: debe reaparecer tras el visibilityTimeout para reintentarse.
        verify(queueClient, never()).deleteMessage(any(), any());
        verify(poisonQueueClient, never()).sendMessage(any(String.class));
    }

    @Test
    void unFalloTransitorioQueSuperaElLimiteDeReintentosSeMandaAPoisonYSeBorraDelOriginal() throws Exception {
        Transaction transaccion = TransaccionFixture.una().conId("tx-1").construir();
        String envelope = envelopeTransaccionIngestada(transaccion);
        // dequeueCount=3 == maxRetries=3: ya se agoto el margen de reintentos.
        llegaALaCola(mensaje(envelope, 3));

        doThrow(new RuntimeException("Cosmos no disponible")).when(scoringService).procesarTransaccion(any());

        poller.poll();

        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
        verify(poisonQueueClient, times(1)).sendMessage(envelope);
    }

    @Test
    void siElEnvioAPoisonFallaElMensajeIgualSeBorraDelOriginalParaNoQuedarEnBucle() throws Exception {
        // El mensaje ya es permanentemente invalido; si ADEMAS la cola de descartes no
        // responde, preferimos perder el registro de auditoria antes que reintentar
        // para siempre un mensaje que nunca va a procesarse con exito.
        String envelope = """
                {"eventType":"ALGO_DESCONOCIDO"}
                """;
        llegaALaCola(mensaje(envelope, 1));
        doThrow(new RuntimeException("cola de descartes no disponible")).when(poisonQueueClient).sendMessage(any(String.class));

        poller.poll();

        verify(queueClient, times(1)).deleteMessage(MESSAGE_ID, POP_RECEIPT);
    }

    @Test
    void siLaLecturaDeLaColaFallaElPollNoPropagaLaExcepcion() {
        when(queueClient.receiveMessages(anyInt(), any(Duration.class), any(), any()))
                .thenThrow(new RuntimeException("cola no disponible"));

        poller.poll();

        verify(scoringService, never()).procesarTransaccion(any());
    }
}
