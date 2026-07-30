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

    private final EmailService emailService;

    public EmailNotificationFunction() {
        this(new AzureCommunicationEmailService());
    }

    EmailNotificationFunction(EmailService emailService) {
        this.emailService = emailService;
    }

    @FunctionName("EmailNotificationFunction")
    public void run(
        @QueueTrigger(
            name = "message",
            queueName = "%FraudQueueName%",
            connection = "AzureWebJobsStorage"
        ) String message,
        ExecutionContext context
    ) {
        Logger logger = context.getLogger();
        try {
            FraudAlertEvent event = OBJECT_MAPPER.readValue(message, FraudAlertEvent.class);
            logger.info(() -> "Procesando alerta de fraude para transaccion " + event.transactionId());
            emailService.send(event);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error procesando FraudAlertEvent, se reintentara via la cola de poison", e);
            throw new RuntimeException(e);
        }
    }
}
