package com.ingesta.service;

import com.azure.storage.queue.QueueClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ingesta.dto.FraudAlertEvent;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AzureQueueFraudAlertEmailPublisher implements FraudAlertEmailPublisher {

    private static final Logger log = LoggerFactory.getLogger(AzureQueueFraudAlertEmailPublisher.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    public AzureQueueFraudAlertEmailPublisher(
            @Qualifier("fraudAlertQueueClient") QueueClient fraudAlertQueueClient) {
        this.queueClient = fraudAlertQueueClient;
        // ObjectMapper propio, no el del contexto: este contrato exige que los Instant salgan
        // como ISO-8601 ("2026-07-30T15:00:00Z"). Sin disable(WRITE_DATES_AS_TIMESTAMPS) salen
        // como numeros y la Function no los puede deserializar.
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Encola la alerta que dispara el correo al analista.
     *
     * El mensaje va como texto plano, nunca en Base64: la Function esta configurada con
     * messageEncoding "none" y, si llega codificado, el host falla al decodificarlo,
     * reintenta 5 veces y manda el mensaje a la cola de poison sin invocar la Function ni
     * dejar un solo log de aplicacion. sendMessage(String) ya escribe texto plano.
     *
     * Tampoco se llama a createQueue(): la cola ya existe y la Identidad Gestionada tiene
     * permisos de datos, no de gestion, asi que esa llamada fallaria.
     *
     * Cualquier fallo se registra sin propagarse: no notificar es menos grave que romper el
     * flujo de scoring, que ya persistio el score y el caso antes de llegar aqui.
     */
    @Override
    @Async("eventoIngestaExecutor")
    public void publicarAlerta(Transaction transaction, TransactionScore score) {
        try {
            String mensaje = objectMapper.writeValueAsString(FraudAlertEvent.of(transaction, score));
            queueClient.sendMessage(mensaje);
            log.info("Alerta de fraude encolada para notificacion por correo. transactionId={} score={}",
                    transaction.transactionId(), score.score());
        } catch (Exception ex) {
            log.error("No se pudo encolar la alerta de correo para la transaccion {}",
                    transaction.transactionId(), ex);
        }
    }
}
