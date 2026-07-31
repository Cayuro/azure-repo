package com.ingesta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Antes este record no llevaba anotaciones y el controller no aplicaba @Valid, asi que
 * PUT /{transactionId}/status aceptaba cualquier cadena arbitraria (o null), corrompiendo
 * el caso de fraude persistido. Los cinco estados vienen del frontend
 * (client/src/components/TransactionDetailCard.jsx, STATUS_OPTIONS) y son los unicos que
 * el sistema entiende; cualquier otro valor se rechaza con 400.
 */
public record UpdateFraudCaseStatusRequest(
        @NotBlank(message = "no puede estar vacio")
        @Pattern(
                regexp = "ABIERTO|EN_REVISION|REVISADO|CERRADO|PENDIENTE",
                message = "debe ser uno de: ABIERTO, EN_REVISION, REVISADO, CERRADO, PENDIENTE"
        )
        String status
) {
}
