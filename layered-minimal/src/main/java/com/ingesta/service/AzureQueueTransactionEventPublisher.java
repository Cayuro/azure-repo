package com.ingesta.service;

import com.azure.storage.queue.QueueClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.dto.TransaccionIngestadaEvento;
import com.ingesta.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AzureQueueTransactionEventPublisher implements TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AzureQueueTransactionEventPublisher.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    public AzureQueueTransactionEventPublisher(QueueClient ingestaQueueClient, ObjectMapper objectMapper) {
        this.queueClient = ingestaQueueClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Se ejecuta en un hilo aparte (eventoIngestaExecutor): el llamador nunca espera la
     * confirmacion de la cola ni, mucho menos, el resultado del scoring del consumidor.
     * Cualquier fallo se registra pero no se propaga, para no afectar una respuesta que
     * ya pudo haberse enviado al cliente.
     */
    @Override
    @Async("eventoIngestaExecutor")
    public void publicarTransaccionIngestada(Transaction transaccion) {
        try {
            String mensaje = objectMapper.writeValueAsString(new TransaccionIngestadaEvento(transaccion));
            queueClient.sendMessage(mensaje);
        } catch (Exception ex) {
            log.error("No se pudo publicar el evento de ingesta para la transaccion {}", transaccion.getTransactionId(), ex);
        }
    }
}
