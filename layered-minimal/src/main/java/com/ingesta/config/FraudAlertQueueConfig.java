package com.ingesta.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente de la cola que consume la Azure Function de envio de correos.
 *
 * Va aparte de {@link AzureQueueConfig} a proposito: esa cola vive en OTRA Storage Account
 * (stcolafraudecentinela) que la de ingesta y evidencias (azure.storage.account-name). Las
 * dos tienen una cola llamada cola-casos-fraude, pero son recursos distintos y solo la de
 * stcolafraudecentinela tiene un consumidor real.
 */
@Configuration
public class FraudAlertQueueConfig {

    @Bean
    public QueueClient fraudAlertQueueClient(
            @Value("${azure.storage.fraud-alert-account}") String accountName,
            @Value("${azure.storage.fraud-alert-queue-name}") String queueName) {
        // Sin claves: la Identidad Gestionada del App Service ya tiene Storage Queue Data
        // Contributor sobre esta cuenta. En local usa la sesion de az login.
        return new QueueClientBuilder()
                .endpoint("https://" + accountName + ".queue.core.windows.net")
                .queueName(queueName)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
