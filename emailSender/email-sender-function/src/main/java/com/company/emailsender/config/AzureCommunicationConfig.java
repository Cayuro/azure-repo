package com.company.emailsender.config;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;

/**
 * Crea y reutiliza el EmailClient de Azure Communication Services a partir de la
 * cadena de conexion. Se cachea en un campo estatico porque el runtime de Azure
 * Functions reutiliza la instancia de la clase entre invocaciones dentro de la
 * misma instancia de host, evitando reconstruir el cliente en cada mensaje.
 */
public final class AzureCommunicationConfig {

    private static final String CONNECTION_STRING_ENV = "CommunicationServicesConnectionString";

    private static volatile EmailClient emailClient;

    private AzureCommunicationConfig() {
    }

    public static EmailClient getEmailClient() {
        EmailClient client = emailClient;
        if (client == null) {
            synchronized (AzureCommunicationConfig.class) {
                client = emailClient;
                if (client == null) {
                    client = buildClient();
                    emailClient = client;
                }
            }
        }
        return client;
    }

    private static EmailClient buildClient() {
        String connectionString = requireEnv(CONNECTION_STRING_ENV);
        return new EmailClientBuilder()
            .connectionString(connectionString)
            .buildClient();
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta configurar la variable de entorno: " + name);
        }
        return value;
    }
}
