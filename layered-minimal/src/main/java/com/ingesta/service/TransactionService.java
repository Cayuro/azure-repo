package com.ingesta.service;

import com.ingesta.dto.TransactionRequest;
import com.ingesta.dto.TransactionResponse;
import com.ingesta.exception.InvalidTransactionException;
import com.ingesta.exception.TransactionNotFoundException;
import com.ingesta.model.Transaction;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.messaging.TransactionEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final TransactionEventPublisher eventPublisher;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, TransactionEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public TransactionResponse ingest(TransactionRequest request) {
        validate(request);

        Instant ingestedAt = Instant.now(clock);
        Transaction transaction = new Transaction(
                request.transactionId(),
                request.accountId(),
                request.amount(),
                request.currency().toUpperCase(),
                request.occurredAt(),
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
        return new TransactionResponse(transaction.transactionId(), "RECIBIDA", transaction.ingestedAt());
    }

    public Transaction getById(String transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private void validate(TransactionRequest request) {
        List<String> errors = new ArrayList<>();
        if (request.occurredAt() != null && request.occurredAt().isAfter(Instant.now(clock))) {
            errors.add("occurredAt no puede ser futura");
        }
        if (!errors.isEmpty()) {
            throw new InvalidTransactionException("La transaccion no cumple el contrato", errors);
        }
    }
}
