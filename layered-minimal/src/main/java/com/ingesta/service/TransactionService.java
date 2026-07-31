package com.ingesta.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ingesta.dto.TransactionRequest;
import com.ingesta.dto.TransactionResponse;
import com.ingesta.exception.InvalidTransactionException;
import com.ingesta.exception.TransactionNotFoundException;
import com.ingesta.messaging.TransactionEventPublisher;
import com.ingesta.model.Transaction;
import com.ingesta.repository.TransactionRepository;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final TransactionEventPublisher eventPublisher;
    private final IngestaQueueEventPublisher ingestaQueueEventPublisher;
    private final Clock clock;

    public TransactionService(
            TransactionRepository repository,
            TransactionEventPublisher eventPublisher,
            IngestaQueueEventPublisher ingestaQueueEventPublisher,
            Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.ingestaQueueEventPublisher = ingestaQueueEventPublisher;
        this.clock = clock;
    }

    public TransactionResponse ingest(TransactionRequest request) {
        validate(request);

        String transactionId = request.transactionId() == null || request.transactionId().isBlank()
                ? generateTransactionId()
                : request.transactionId();

        Instant ingestedAt = Instant.now(clock);
        Instant occurredAt = (request.occurredAt() != null) ? request.occurredAt() : ingestedAt;

        Transaction transaction = new Transaction(
                transactionId,
                request.accountId(),
                request.amount(),
                request.currency().toUpperCase(),
                occurredAt,
                ingestedAt,
                request.latitude(),
                request.longitude(),
                request.merchantId(),
                request.merchantCategory()
        );

        TransactionRepository.SaveOutcome outcome = repository.saveIfAbsent(transaction);
        if (outcome == TransactionRepository.SaveOutcome.ALREADY_EXISTS) {
            Transaction existingTransaction = repository.findById(transaction.transactionId())
                    .orElse(transaction);
            return new TransactionResponse(existingTransaction.transactionId(), "YA_RECIBIDA", existingTransaction.ingestedAt());
        }
        eventPublisher.publish(transaction);
        ingestaQueueEventPublisher.publicarTransaccionIngestada(transaction);
        return new TransactionResponse(transaction.transactionId(), "RECIBIDA", transaction.ingestedAt());
    }

    private String generateTransactionId() {
        // Antes usaba un AtomicLong en memoria (TXN-1, TXN-2...), que reinicia en cada
        // arranque o replica. Dos instancias (o un reinicio) volvian a emitir TXN-1, y una
        // transaccion NUEVA con ese id colisionaba con una VIEJA, devolviendo 200
        // YA_RECIBIDA y descartando datos financieros en silencio. UUID es unico entre
        // instancias y reinicios sin necesitar coordinacion.
        return "TXN-" + UUID.randomUUID();
    }

    public Transaction getById(String transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    public List<Transaction> getAll() {
        return repository.findAll();
    }

    private void validate(TransactionRequest request) {
        List<String> errors = new ArrayList<>();

        // Se usa el Clock inyectado (no Instant.now() directo) para que "ahora" sea
        // deterministico en los tests y consistente con el resto del servicio. Sin este
        // control, una transaccion con occurredAt en el futuro (p.ej. anio 2999) se
        // aceptaba con 202 y contaminaba el motor de scoring con datos imposibles.
        Instant now = Instant.now(clock);
        if (request.occurredAt() != null && request.occurredAt().isAfter(now)) {
            errors.add("occurredAt: no puede ser una fecha futura");
        }

        if (!errors.isEmpty()) {
            throw new InvalidTransactionException("La transaccion no cumple el contrato", errors);
        }
    }
}
