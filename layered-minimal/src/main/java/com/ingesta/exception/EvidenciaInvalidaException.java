package com.ingesta.exception;

/**
 * Excepcion de dominio para validaciones de evidencias (tamano, tipo de archivo real,
 * nombre de blob, existencia) que SI son seguras de mostrar al usuario final: el
 * frontend ya las renderiza tal cual.
 *
 * Existe para separarlas de IllegalArgumentException generica, que tambien puede venir
 * del SDK de Azure (Cosmos, Blob Storage) o de MediaType.parseMediaType con mensajes que
 * revelan detalles internos (endpoints, nombres de contenedores, configuracion). Ver
 * GlobalExceptionHandler: solo esta excepcion propaga su mensaje tal cual; cualquier otra
 * IllegalArgumentException recibe un mensaje generico.
 */
public class EvidenciaInvalidaException extends RuntimeException {

    public EvidenciaInvalidaException(String message) {
        super(message);
    }
}
