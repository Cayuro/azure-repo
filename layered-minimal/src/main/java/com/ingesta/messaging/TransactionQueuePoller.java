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
import java.util.List;

/**
 * Consumidor real de cola-transacciones-ingesta: cierra la brecha de que el scoring se
 * disparaba unicamente por un evento de Spring in-memory (ver TransactionScoringService),
 * que no sobrevive a un reinicio ni se comparte entre instancias. Este poller procesa el
 * mensaje que la API ya publico de forma asincrona tras persistir la transaccion.
 *
 * Semantica at-least-once: el mensaje solo se borra (deleteMessage) si el procesamiento
 * fue exitoso. Si falla, se deja en la cola: reaparece al expirar el visibilityTimeout y
 * se reintenta; tras varios reintentos (dequeueCount), Azure lo mueve a la cola poison
 * (ver docs/resiliencia-cola-ingesta.md). TransactionScoringService.procesarTransaccion
 * es idempotente, asi que un reintento nunca duplica el score ni el caso de fraude.
 */
@Component
public class TransactionQueuePoller {

    private static final Logger log = LoggerFactory.getLogger(TransactionQueuePoller.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;
    private final TransactionScoringService scoringService;
    private final TransactionRepository transactionRepository;

    @Value("${ingesta.queue.max-messages:5}")
    private int maxMessages;

    @Value("${ingesta.queue.visibility-timeout-seconds:30}")
    private int visibilityTimeoutSeconds;

    public TransactionQueuePoller(
            @Qualifier("ingestaQueueClient") QueueClient queueClient,
            ObjectMapper objectMapper,
            TransactionScoringService scoringService,
            TransactionRepository transactionRepository) {
        this.queueClient = queueClient;
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
            try {
                // El mensaje NO viene en Base64 (default de QueueClientBuilder es
                // QueueMessageEncoding.NONE, y ninguno de nuestros publishers lo cambia).
                JsonNode raiz = objectMapper.readTree(message.getMessageText());
                Transaction transaction = objectMapper.treeToValue(raiz.get("data"), Transaction.class);

                transactionRepository.saveIfAbsent(transaction);
                scoringService.procesarTransaccion(transaction);

                queueClient.deleteMessage(message.getMessageId(), message.getPopReceipt());
            } catch (Exception ex) {
                log.error("Error procesando mensaje {} de cola-transacciones-ingesta -- se reintentara (dequeueCount={})",
                        message.getMessageId(), message.getDequeueCount(), ex);
                // No se llama deleteMessage: el mensaje reaparece tras el visibilityTimeout.
            }
        }
    }
}
