package com.ingesta.dto;

import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Mensaje que se publica en cola-casos-fraude (cuenta stcolafraudecentinela) y que consume
 * la Azure Function de envio de correos.
 *
 * Es un contrato con un sistema externo: estos campos deben coincidir uno a uno, en nombre y
 * tipo, con el record {@code com.company.emailsender.dto.FraudAlertEvent} de la Function.
 * La Function deserializa con la configuracion por defecto de Jackson, asi que un campo de
 * mas hace fallar el mensaje completo (se reintenta 5 veces y acaba en la cola de poison sin
 * generar ni un log). Antes de agregar un campo aqui hay que agregarlo alli.
 *
 * Ver docs-email-sender/02-formato-mensaje-cola.md.
 */
public record FraudAlertEvent(
        String transactionId,
        String accountId,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        Instant ingestedAt,
        Double latitude,
        Double longitude,
        String merchantId,
        String merchantCategory,
        int score,
        int threshold,
        Instant scoredAt,
        List<FraudAlertRuleActivation> activations
) {

    /**
     * El correo describe la transaccion y por que se marco, no el caso abierto: por eso se
     * construye desde la transaccion y su score, sin necesitar el FraudCase.
     */
    public static FraudAlertEvent of(Transaction transaction, TransactionScore score) {
        return new FraudAlertEvent(
                transaction.transactionId(),
                transaction.accountId(),
                transaction.amount(),
                transaction.currency(),
                transaction.occurredAt(),
                transaction.ingestedAt(),
                transaction.latitude(),
                transaction.longitude(),
                transaction.merchantId(),
                transaction.merchantCategory(),
                score.score(),
                score.threshold(),
                score.scoredAt(),
                score.activations().stream().map(FraudAlertRuleActivation::from).toList());
    }
}
