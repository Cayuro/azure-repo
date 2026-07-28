package com.ingesta.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiDocumentation() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de ingesta financiera")
                        .description("API para recibir transacciones, calcular score y consultar evidencias")
                        .version("1.0.0"))
                .externalDocs(new ExternalDocumentation()
                        .description("Swagger UI")
                        .url("/swagger-ui.html"));
    }
}