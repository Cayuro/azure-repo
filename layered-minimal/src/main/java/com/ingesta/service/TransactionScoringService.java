package com.ingesta.service;

import com.ingesta.dto.RiesgoResponse;
import com.ingesta.messaging.TransactionIngestedEvent;
import com.ingesta.model.FraudCase;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import com.ingesta.repository.FraudCaseRepository;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.repository.TransactionScoreRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransactionScoringService {

    private final TransactionRepository transactionRepository;
    private final TransactionScoreRepository scoreRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final TransactionScoringEngine scoringEngine;
    private final FraudCaseEventPublisher fraudCaseEventPublisher;
    private final Clock clock;

    // Guarda atomica: evita que el evento in-process y el poller de la cola procesen la
    // misma transaccion en paralelo (el check-then-act sobre scoreRepository no es atomico
    // por si solo, y ambos caminos pueden dispararse practicamente al mismo tiempo).
    private final Set<String> transaccionesEnProceso = ConcurrentHashMap.newKeySet();

    public TransactionScoringService(
            TransactionRepository transactionRepository,
            TransactionScoreRepository scoreRepository,
            FraudCaseRepository fraudCaseRepository,
            TransactionScoringEngine scoringEngine,
            FraudCaseEventPublisher fraudCaseEventPublisher,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.scoreRepository = scoreRepository;
        this.fraudCaseRepository = fraudCaseRepository;
        this.scoringEngine = scoringEngine;
        this.fraudCaseEventPublisher = fraudCaseEventPublisher;
        this.clock = clock;
    }

    /**
     * Disparo en proceso (evento de Spring in-memory): mismo camino de siempre, para
     * baja latencia mientras la misma instancia sigue viva.
     */
    @EventListener
    public void onTransactionIngested(TransactionIngestedEvent event) {
        procesarTransaccion(event.transaction());
    }

    /**
     * Calcula el score y abre el caso de fraude si corresponde. Idempotente: si la
     * transaccion ya fue procesada (por el evento in-process o por un intento anterior
     * de {@code TransactionQueuePoller}), no se reprocesa. Esto permite que ambos
     * caminos (evento in-process y el consumidor real de cola-transacciones-ingesta)
     * disparen este metodo sin generar scores/casos duplicados, y que un mensaje
     * reentregado tras un crash a mitad de proceso (semantica at-least-once) se procese
     * como no-op en vez de duplicar el efecto.
     */
    public void procesarTransaccion(Transaction transaction) {
        String transactionId = transaction.transactionId();
        if (scoreRepository.findByTransactionId(transactionId).isPresent()) {
            return;
        }
        if (!transaccionesEnProceso.add(transactionId)) {
            return; // otro hilo (evento in-process o poller) ya esta procesando esta transaccion
        }

        try {
            List<Transaction> history = transactionRepository.findByAccountId(transaction.accountId()).stream()
                    .filter(item -> !item.transactionId().equals(transactionId))
                    .toList();

            TransactionScore score = scoringEngine.score(transaction, history);
            scoreRepository.save(score);

            if (score.score() > score.threshold()) {
                FraudCase fraudCase = new FraudCase(
                        UUID.randomUUID().toString(),
                        transactionId,
                        score.score(),
                        "ABIERTO",
                        Instant.now(clock),
                        score.activations());
                fraudCaseRepository.save(fraudCase);
                fraudCaseEventPublisher.publicarCasoFraude(fraudCase);
            }
        } finally {
            transaccionesEnProceso.remove(transactionId);
        }
    }

    public RiesgoResponse obtenerRiesgo(String transactionId) {
        return scoreRepository.findByTransactionId(transactionId)
                .map(score -> RiesgoResponse.of(score, fraudCaseRepository.findByTransactionId(transactionId)))
                .orElseGet(() -> RiesgoResponse.pending(transactionId));
    }
}