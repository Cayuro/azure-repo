package com.ingesta.service;

import com.azure.storage.queue.QueueClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.dto.CasoFraudeDetectadoEvento;
import com.ingesta.model.FraudCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AzureQueueFraudCaseEventPublisher implements FraudCaseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AzureQueueFraudCaseEventPublisher.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    public AzureQueueFraudCaseEventPublisher(
            @Qualifier("casosFraudeQueueClient") QueueClient casosFraudeQueueClient,
            ObjectMapper objectMapper) {
        this.queueClient = casosFraudeQueueClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Publica en cola-casos-fraude (Azure Storage Queue): el mensaje permanece en la cola
     * hasta que el consumidor lo borra tras procesarlo con exito, asi que si el consumidor
     * (equipo analitico) esta caido o se cae a mitad de proceso, el caso no se pierde, solo
     * queda invisible durante el visibilityTimeout y vuelve a aparecer para reintento (ver
     * docs/resiliencia-cola-ingesta.md). Cualquier fallo al encolar se registra sin propagarse,
     * para no bloquear ni afectar al llamador.
     */
    @Override
    @Async("eventoIngestaExecutor")
    public void publicarCasoFraude(FraudCase caso) {
        try {
            String mensaje = objectMapper.writeValueAsString(new CasoFraudeDetectadoEvento(caso));
            queueClient.sendMessage(mensaje);
        } catch (Exception ex) {
            log.error("No se pudo publicar el caso de fraude {} para la transaccion {}",
                    caso.caseId(), caso.transactionId(), ex);
        }
    }
}
