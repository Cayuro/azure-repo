package com.company.emailsender.function;

import com.company.emailsender.dto.FraudAlertEvent;
import com.company.emailsender.service.AzureCommunicationEmailService;
import com.company.emailsender.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;

import java.util.logging.Level;
import java.util.logging.Logger;

public class EmailNotificationFunction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Se construye de forma perezosa dentro de run(), no en el constructor: si la
     * creacion del cliente de ACS falla (falta una app setting, credencial invalida...),
     * la excepcion tiene que ocurrir dentro del try/catch para quedar logueada. Si se
     * construye en el constructor, el worker de Java falla al instanciar la clase, la
     * invocacion se reintenta hasta la cola de poison y no queda ni un solo log.
     */
    private EmailService emailService;

    public EmailNotificationFunction() {
    }

    EmailNotificationFunction(EmailService emailService) {
        this.emailService = emailService;
    }

    private EmailService emailService() {
        if (emailService == null) {
            emailService = new AzureCommunicationEmailService();
        }
        return emailService;
    }

    @FunctionName("EmailNotificationFunction")
    public void run(
        @QueueTrigger(
            name = "message",
            queueName = "%FraudQueueName%",
            connection = "FraudQueueStorage"
        ) String message,
        ExecutionContext context
    ) {
        Logger logger = context.getLogger();
        try {
            logger.info("EmailNotificationFunction invocada, deserializando mensaje...");
            FraudAlertEvent event = OBJECT_MAPPER.readValue(message, FraudAlertEvent.class);
            logger.info(() -> "Procesando alerta de fraude para transaccion " + event.transactionId());
            emailService().send(event);
        } catch (Exception e) {
            // Se loguea el mensaje de la excepcion aparte del stack trace: en Azure
            // Functions el stack a veces no llega completo a Application Insights.
            logger.log(Level.SEVERE, "Error procesando FraudAlertEvent: " + e, e);
            throw new RuntimeException(e);
        }
    }
}
