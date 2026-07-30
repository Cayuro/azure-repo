package com.company.emailsender.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailAddress;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.email.models.EmailSendResult;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.company.emailsender.config.AzureCommunicationConfig;
import com.company.emailsender.dto.FraudAlertEvent;
import com.company.emailsender.template.FraudEmailTemplateBuilder;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class AzureCommunicationEmailService implements EmailService {

    private static final Logger LOGGER = Logger.getLogger(AzureCommunicationEmailService.class.getName());
    private static final Duration SEND_TIMEOUT = Duration.ofMinutes(2);

    private final EmailClient emailClient;
    private final FraudEmailTemplateBuilder templateBuilder;
    private final String senderAddress;
    private final List<String> recipientAddresses;

    public AzureCommunicationEmailService() {
        this(
            AzureCommunicationConfig.getEmailClient(),
            new FraudEmailTemplateBuilder(),
            requireEnv("EmailSenderAddress"),
            splitRecipients(requireEnv("FraudAlertRecipients"))
        );
    }

    AzureCommunicationEmailService(EmailClient emailClient,
                                    FraudEmailTemplateBuilder templateBuilder,
                                    String senderAddress,
                                    List<String> recipientAddresses) {
        this.emailClient = emailClient;
        this.templateBuilder = templateBuilder;
        this.senderAddress = senderAddress;
        this.recipientAddresses = recipientAddresses;
    }

    @Override
    public void send(FraudAlertEvent event) {
        String subject = templateBuilder.buildSubject(event);
        String html = templateBuilder.buildHtml(event);

        EmailMessage message = new EmailMessage()
            .setSenderAddress(senderAddress)
            .setSubject(subject)
            .setBodyHtml(html);

        message.setToRecipients(recipientAddresses.stream()
            .map(EmailAddress::new)
            .toArray(EmailAddress[]::new));

        SyncPoller<EmailSendResult, EmailSendResult> poller = emailClient.beginSend(message);
        PollResponse<EmailSendResult> response = poller.waitForCompletion(SEND_TIMEOUT);
        EmailSendResult result = response.getValue();

        LOGGER.info(() -> "Correo de alerta de fraude enviado. transactionId=" + event.transactionId()
            + " operationId=" + result.getId() + " status=" + result.getStatus());
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta configurar la variable de entorno: " + name);
        }
        return value;
    }

    private static List<String> splitRecipients(String csv) {
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
