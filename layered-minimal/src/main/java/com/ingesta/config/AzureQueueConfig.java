package com.ingesta.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureQueueConfig {

    @Bean
    public QueueClient ingestaQueueClient(
            @Value("${azure.storage.account-name}") String accountName,
            @Value("${azure.storage.queue-name}") String queueName) {
        // Conexion sin claves usando Identidad Gestionada / az login (RBAC de Azure)
        return new QueueClientBuilder()
                .endpoint("https://" + accountName + ".queue.core.windows.net")
                .queueName(queueName)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
