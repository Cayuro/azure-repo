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

    @Bean
    public QueueClient casosFraudeQueueClient(
            @Value("${azure.storage.account-name}") String accountName,
            @Value("${azure.storage.fraud-queue-name}") String queueName) {
        // Conexion sin claves usando Identidad Gestionada / az login (RBAC de Azure)
        return new QueueClientBuilder()
                .endpoint("https://" + accountName + ".queue.core.windows.net")
                .queueName(queueName)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

    /**
     * Cola de descartes de TransactionQueuePoller: destino final de los mensajes que NO
     * tiene sentido reintentar (permanentemente invalidos) o que agotaron el limite de
     * reintentos configurable (ingesta.queue.max-retries) tras fallos transitorios. Azure
     * Storage Queues no ofrece dead-lettering nativo (eso lo da el runtime de Azure
     * Functions, no el servicio de Storage), asi que esta cola es nuestra implementacion
     * manual de ese concepto: preserva el mensaje para investigacion en vez de perderlo
     * en silencio cuando expire su TTL de 7 dias.
     */
    @Bean
    public QueueClient ingestaPoisonQueueClient(
            @Value("${azure.storage.account-name}") String accountName,
            @Value("${azure.storage.poison-queue-name}") String queueName) {
        // Conexion sin claves usando Identidad Gestionada / az login (RBAC de Azure)
        return new QueueClientBuilder()
                .endpoint("https://" + accountName + ".queue.core.windows.net")
                .queueName(queueName)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }

}
