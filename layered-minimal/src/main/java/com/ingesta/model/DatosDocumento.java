package com.ingesta.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record DatosDocumento(
        String transactionId,
        String blobName,
        String nombre,
        String numeroIdentificacion,
        Map<String, LocalDate> fechas,
        Instant extractedAt
) {
    public DatosDocumento {
        fechas = Map.copyOf(fechas);
    }
}
