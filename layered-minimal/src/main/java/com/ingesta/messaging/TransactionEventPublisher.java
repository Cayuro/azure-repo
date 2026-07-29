package com.ingesta.messaging;

import com.ingesta.model.Transaction;

/**
 * Interfaz genérica para la publicación de eventos relacionados con transacciones.
 * Actúa como un contrato para diferentes mecanismos de publicación (e.g., local, Azure)..
 */
public interface TransactionEventPublisher {

    /**
     * Publica un evento basado en una transacción.
     * @param transaction La transacción a publicar.
     */
    void publish(Transaction transaction);
}