package com.ingesta.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
    private final AtomicLong transactionSequence = new AtomicLong();

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
        return "TXN-" + transactionSequence.incrementAndGet();
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
        if (!errors.isEmpty()) {
            throw new InvalidTransactionException("La transaccion no cumple el contrato", errors);
        }
    }
}
