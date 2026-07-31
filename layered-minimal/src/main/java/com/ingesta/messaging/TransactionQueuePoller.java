package com.ingesta.messaging;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.model.Transaction;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.service.TransactionScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumidor real de cola-transacciones-ingesta: cierra la brecha de que el scoring se
 * disparaba unicamente por un evento de Spring in-memory (ver TransactionScoringService),
 * que no sobrevive a un reinicio ni se comparte entre instancias. Este poller procesa el
 * mensaje que la API ya publico de forma asincrona tras persistir la transaccion.
 *
 * cola-transacciones-ingesta es compartida por mas de un publicador (tambien recibe
 * DOCUMENTO_PROCESADO de AzureQueueDocumentoProcesadoEventPublisher): este poller lee el
 * campo eventType del envelope y SOLO procesa como transaccion los mensajes
 * TRANSACCION_INGESTADA. Los eventType conocidos pero ajenos a este poller se descartan
 * (delete + log informativo) porque son notificaciones best-effort de otro consumidor,
 * no trabajo de este poller; un eventType desconocido tambien se descarta (delete + log
 * de advertencia) para no quedar en bucle. Antes de este fix el poller ignoraba
 * eventType por completo e intentaba deserializar CUALQUIER mensaje como Transaction: un
 * DOCUMENTO_PROCESADO fallaba esa deserializacion, el mensaje nunca se borraba y
 * reaparecia cada 30s para siempre (envenenando la cola y ocupando un slot del lote).
 *
 * Semantica at-least-once: el mensaje solo se borra (deleteMessage) si el procesamiento
 * fue exitoso, si es descartable (eventType ajeno/desconocido), o si es permanentemente
 * invalido o supero el limite de reintentos (en estos dos ultimos casos, antes de
 * borrarlo se copia a cola-transacciones-ingesta-poison para no perder evidencia). Azure
 * Storage Queues NO tiene dead-lettering nativo (eso lo aporta el runtime de Azure
 * Functions, no el servicio de Storage): sin un limite de reintentos propio, un mensaje
 * que falla siempre se reprocesaria hasta agotar el TTL (7 dias) y se perderia en
 * silencio. TransactionScoringService.procesarTransaccion es idempotente, asi que un
 * reintento nunca duplica el score ni el caso de fraude.
 */
@Component
public class TransactionQueuePoller {

    private static final Logger log = LoggerFactory.getLogger(TransactionQueuePoller.class);

    // Deben coincidir exactamente con los DTOs de com.ingesta.dto (TransaccionIngestadaEvento,
    // DocumentoProcesadoEvento, CasoFraudeDetectadoEvento): son quienes fijan el valor real.
    private static final String EVENT_TYPE_TRANSACCION_INGESTADA = "TRANSACCION_INGESTADA";
    private static final String EVENT_TYPE_DOCUMENTO_PROCESADO = "DOCUMENTO_PROCESADO";
    private static final String EVENT_TYPE_CASO_FRAUDE_DETECTADO = "CASO_FRAUDE_DETECTADO";

    private final QueueClient queueClient;
    private final QueueClient poisonQueueClient;
    private final ObjectMapper objectMapper;
    private final TransactionScoringService scoringService;
    private final TransactionRepository transactionRepository;

    @Value("${ingesta.queue.max-messages:5}")
    private int maxMessages;

    @Value("${ingesta.queue.visibility-timeout-seconds:30}")
    private int visibilityTimeoutSeconds;

    @Value("${ingesta.queue.max-retries:5}")
    private int maxRetries;

    public TransactionQueuePoller(
            @Qualifier("ingestaQueueClient") QueueClient queueClient,
            @Qualifier("ingestaPoisonQueueClient") QueueClient poisonQueueClient,
            ObjectMapper objectMapper,
            TransactionScoringService scoringService,
            TransactionRepository transactionRepository) {
        this.queueClient = queueClient;
        this.poisonQueueClient = poisonQueueClient;
        this.objectMapper = objectMapper;
        this.scoringService = scoringService;
        this.transactionRepository = transactionRepository;
    }

    @Scheduled(fixedDelayString = "${ingesta.queue.poll-interval-ms:2000}")
    public void poll() {
        List<QueueMessageItem> messages;
        try {
            messages = queueClient.receiveMessages(maxMessages, Duration.ofSeconds(visibilityTimeoutSeconds), null, null)
                    .stream()
                    .toList();
        } catch (Exception ex) {
            log.warn("Error al leer de cola-transacciones-ingesta: {}", ex.getMessage());
            return;
        }

        for (QueueMessageItem message : messages) {
            procesarMensaje(message);
        }
    }

    private void procesarMensaje(QueueMessageItem message) {
        JsonNode raiz;
        String eventType;
        try {
            // El mensaje NO viene en Base64 (default de QueueClientBuilder es
            // QueueMessageEncoding.NONE, y ninguno de nuestros publishers lo cambia).
            raiz = objectMapper.readTree(message.getMessageText());
            JsonNode eventTypeNode = (raiz == null) ? null : raiz.get("eventType");
            if (eventTypeNode == null || eventTypeNode.isNull()) {
                throw new MensajeIngestaInvalidoException("El envelope no trae 'eventType'");
            }
            eventType = eventTypeNode.asText();
        } catch (MensajeIngestaInvalidoException ex) {
            moverAPoisonYBorrar(message, ex.getMessage());
            return;
        } catch (Exception ex) {
            // JSON malformado: no es un problema de infraestructura, es el contenido del
            // mensaje. Reintentarlo no lo va a arreglar -- es permanente.
            log.error("Mensaje {} de cola-transacciones-ingesta no es JSON valido, se descarta de forma permanente",
                    message.getMessageId(), ex);
            moverAPoisonYBorrar(message, "JSON invalido: " + ex.getMessage());
            return;
        }

        switch (eventType) {
            case EVENT_TYPE_TRANSACCION_INGESTADA -> procesarTransaccionIngestada(message, raiz);
            case EVENT_TYPE_DOCUMENTO_PROCESADO, EVENT_TYPE_CASO_FRAUDE_DETECTADO -> {
                // No es un error: es una notificacion best-effort de otro publicador que
                // comparte esta cola y que este poller no consume. Se borra sin reintentar.
                log.info("Descartando mensaje {} de cola-transacciones-ingesta con eventType={}: no le corresponde a este poller",
                        message.getMessageId(), eventType);
                queueClient.deleteMessage(message.getMessageId(), message.getPopReceipt());
            }
            default -> {
                log.warn("Mensaje {} de cola-transacciones-ingesta con eventType desconocido '{}', se descarta para no quedar en bucle",
                        message.getMessageId(), eventType);
                moverAPoisonYBorrar(message, "eventType desconocido: " + eventType);
            }
        }
    }

    private void procesarTransaccionIngestada(QueueMessageItem message, JsonNode raiz) {
        try {
            JsonNode dataNode = raiz.get("data");
            if (dataNode == null || dataNode.isNull()) {
                throw new MensajeIngestaInvalidoException(
                        "El envelope TRANSACCION_INGESTADA no trae 'data'");
            }

            Transaction transaction;
            try {
                transaction = objectMapper.treeToValue(dataNode, Transaction.class);
            } catch (Exception ex) {
                throw new MensajeIngestaInvalidoException(
                        "El campo 'data' no se pudo interpretar como Transaction: " + ex.getMessage(), ex);
            }

            validarTransaccion(transaction);

            transactionRepository.saveIfAbsent(transaction);
            scoringService.procesarTransaccion(transaction);

            queueClient.deleteMessage(message.getMessageId(), message.getPopReceipt());
        } catch (MensajeIngestaInvalidoException ex) {
            // PERMANENTE: el mismo mensaje va a fallar exactamente igual en cada
            // reintento (dequeueCount no cambia el contenido), asi que insistir solo
            // consume los 7 dias de TTL antes de perderlo en silencio.
            log.error("Mensaje {} de cola-transacciones-ingesta es invalido de forma permanente ({}), se mueve a la cola de descartes",
                    message.getMessageId(), ex.getMessage());
            moverAPoisonYBorrar(message, ex.getMessage());
        } catch (Exception ex) {
            // TRANSITORIO (BD/Cosmos caida, fallo de publicacion no capturado, etc.): el
            // mismo mensaje SI podria procesarse con exito mas adelante, asi que tiene
            // sentido reintentar -- pero acotado por maxRetries, no eternamente.
            long dequeueCount = message.getDequeueCount();
            if (dequeueCount >= maxRetries) {
                log.warn("Mensaje {} de cola-transacciones-ingesta supero el limite de reintentos ({}, dequeueCount={}) tras un error transitorio, se mueve a la cola de descartes",
                        message.getMessageId(), maxRetries, dequeueCount, ex);
                moverAPoisonYBorrar(message, "Limite de reintentos superado tras error transitorio: " + ex.getMessage());
            } else {
                log.error("Error transitorio procesando mensaje {} de cola-transacciones-ingesta -- se reintentara (dequeueCount={})",
                        message.getMessageId(), dequeueCount, ex);
                // No se llama deleteMessage: el mensaje reaparece tras el visibilityTimeout.
            }
        }
    }

    /**
     * Valida el objeto ya deserializado antes de que llegue al motor de scoring. El
     * camino HTTP (TransactionRequest) valida con @Valid; este camino no pasaba por ahi
     * y un campo nulo (amount, latitude, longitude, merchantCategory...) producia un
     * NullPointerException opaco dentro de TransactionScoringEngine en vez de un error
     * claro de "mensaje invalido".
     */
    private void validarTransaccion(Transaction transaction) {
        List<String> camposInvalidos = new ArrayList<>();

        if (isBlank(transaction.transactionId())) {
            camposInvalidos.add("transactionId");
        }
        if (isBlank(transaction.accountId())) {
            camposInvalidos.add("accountId");
        }
        if (transaction.amount() == null) {
            camposInvalidos.add("amount");
        }
        if (isBlank(transaction.currency())) {
            camposInvalidos.add("currency");
        }
        if (transaction.occurredAt() == null) {
            camposInvalidos.add("occurredAt");
        }
        if (transaction.ingestedAt() == null) {
            camposInvalidos.add("ingestedAt");
        }
        if (transaction.latitude() == null) {
            camposInvalidos.add("latitude");
        }
        if (transaction.longitude() == null) {
            camposInvalidos.add("longitude");
        }
        if (isBlank(transaction.merchantId())) {
            camposInvalidos.add("merchantId");
        }
        if (isBlank(transaction.merchantCategory())) {
            camposInvalidos.add("merchantCategory");
        }

        if (!camposInvalidos.isEmpty()) {
            throw new MensajeIngestaInvalidoException(
                    "Transaction invalida, campos faltantes: " + camposInvalidos);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Destino final de un mensaje que no tiene sentido reintentar: se copia a
     * cola-transacciones-ingesta-poison (para que quede disponible para investigacion) y
     * se borra del original. Si el envio a la cola de descartes falla, se registra el
     * error pero el mensaje se borra igual del original: la alternativa (dejarlo) es
     * exactamente el bug que se esta corrigiendo, un mensaje que jamas se procesara
     * reapareciendo cada visibilityTimeout para siempre.
     */
    private void moverAPoisonYBorrar(QueueMessageItem message, String motivo) {
        try {
            poisonQueueClient.sendMessage(message.getMessageText());
        } catch (Exception ex) {
            log.error("No se pudo copiar el mensaje {} a cola-transacciones-ingesta-poison (motivo original: {}); se borra igual del original",
                    message.getMessageId(), motivo, ex);
        } finally {
            queueClient.deleteMessage(message.getMessageId(), message.getPopReceipt());
        }
    }
}
