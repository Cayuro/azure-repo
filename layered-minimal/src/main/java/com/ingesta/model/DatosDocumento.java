package com.ingesta.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record DatosDocumento(
        String transactionId,
        String blobName,
        EstadoProcesamiento estado,
        String nombre,
        String numeroIdentificacion,
        Map<String, LocalDate> fechas,
        String motivoFallo,
        Instant procesadoEn
) {
    public DatosDocumento {
        fechas = fechas == null ? Map.of() : Map.copyOf(fechas);
    }

    public static DatosDocumento completado(
            String transactionId, String blobName, String nombre,
            String numeroIdentificacion, Map<String, LocalDate> fechas, Instant procesadoEn) {
        return new DatosDocumento(
                transactionId, blobName, EstadoProcesamiento.COMPLETADO, nombre, numeroIdentificacion, fechas, null, procesadoEn);
    }

    public static DatosDocumento fallido(String transactionId, String blobName, String motivoFallo, Instant procesadoEn) {
        return new DatosDocumento(
                transactionId, blobName, EstadoProcesamiento.FALLIDO, null, null, Map.of(), motivoFallo, procesadoEn);
    }

    public enum EstadoProcesamiento {
        COMPLETADO,
        FALLIDO
    }
}
