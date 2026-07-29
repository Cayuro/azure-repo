package com.ingesta.config;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureDocumentIntelligenceConfig {

    @Bean
    public DocumentIntelligenceClient documentIntelligenceClient(
            @Value("${azure.documentintelligence.endpoint}") String endpoint) {
        // Conexion sin claves usando Identidad Gestionada / az login (RBAC de Azure)
        return new DocumentIntelligenceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
