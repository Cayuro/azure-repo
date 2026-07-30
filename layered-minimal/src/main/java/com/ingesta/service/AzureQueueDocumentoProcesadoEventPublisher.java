package com.ingesta.service;

import com.azure.storage.queue.QueueClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.dto.DocumentoProcesadoEvento;
import com.ingesta.model.DatosDocumento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AzureQueueDocumentoProcesadoEventPublisher implements DocumentoProcesadoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AzureQueueDocumentoProcesadoEventPublisher.class);

    private final QueueClient queueClient;
    private final ObjectMapper objectMapper;

    public AzureQueueDocumentoProcesadoEventPublisher(
            @Qualifier("ingestaQueueClient") QueueClient ingestaQueueClient,
            ObjectMapper objectMapper) {
        this.queueClient = ingestaQueueClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Notifica al equipo analitico el resultado (exito o fallo) del reconocimiento
     * documental de una evidencia, para que el documento quede consultable sin
     * necesidad de que el analista este mirando activamente la API. Se ejecuta en
     * un hilo aparte y nunca propaga fallos: un problema al notificar no debe volver
     * a interrumpir el flujo que ya estamos protegiendo con este mecanismo.
     *
     * Reutiliza cola-transacciones-ingesta en vez de una cola dedicada: ambos son
     * eventos de notificacion best-effort del mismo nivel de criticidad (a diferencia
     * de cola-casos-fraude, que es una cola de trabajo con garantia dura de entrega),
     * se distinguen por el campo eventType del envelope. Ver docs/justificacion-eventos-vs-colas.md.
     */
    @Override
    @Async("eventoIngestaExecutor")
    public void notificarResultado(DatosDocumento datos) {
        try {
            String mensaje = objectMapper.writeValueAsString(new DocumentoProcesadoEvento(datos));
            queueClient.sendMessage(mensaje);
        } catch (Exception ex) {
            log.error("No se pudo notificar el resultado del procesamiento documental de la transaccion {}",
                    datos.transactionId(), ex);
        }
    }
}
