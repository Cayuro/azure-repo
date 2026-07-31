package com.ingesta.messaging;

/**
 * Marca un fallo PERMANENTE al procesar un mensaje de cola-transacciones-ingesta: el
 * envelope no es JSON valido, no trae 'eventType'/'data', o el objeto deserializado no
 * cumple el contrato minimo (campos nulos que harian NPE en el motor de scoring).
 *
 * La distincion con un fallo transitorio (BD caida, publicacion fallida) importa porque
 * un reintento NUNCA arregla este tipo de error: el mensaje es el mismo en cada
 * dequeueCount, asi que reintentarlo indefinidamente solo desperdicia los 7 dias de TTL
 * antes de perderse en silencio. Ver {@link TransactionQueuePoller}.
 */
public class MensajeIngestaInvalidoException extends RuntimeException {

    public MensajeIngestaInvalidoException(String message) {
        super(message);
    }

    public MensajeIngestaInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
