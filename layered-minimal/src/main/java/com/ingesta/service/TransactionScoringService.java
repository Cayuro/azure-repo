package com.ingesta.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.ingesta.dto.RiesgoResponse;
import com.ingesta.messaging.TransactionIngestedEvent;
import com.ingesta.model.FraudCase;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import com.ingesta.repository.FraudCaseRepository;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.repository.TransactionScoreRepository;
import java.util.Set;
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
     * Calcula el score y abre el caso de fraude si corresponde. Idempotente frente a
     * reintentos (evento in-process, poller de cola, o un mensaje reentregado tras un
     * crash a mitad de proceso): la condicion de "ya no hay nada pendiente" no es solo
     * "existe un score" -- si el score ya supera el umbral pero el caso de fraude aun no
     * se creo (el proceso murio justo entre guardar el score y crear/publicar el caso),
     * SI hay trabajo pendiente y este metodo debe completarlo, nunca saltarselo. Saltarlo
     * perdia para siempre el caso de fraude exacto que el sistema existe para detectar.
     *
     * La guarda de concurrencia (transaccionesEnProceso.add) se reserva ANTES de leer
     * cualquier estado, no despues: si se leyera antes, un hilo podria pasar esa lectura,
     * desprogramarse, dejar que otro hilo procese la transaccion COMPLETA (incluyendo el
     * remove() de su finally), y solo entonces hacer su propio add() con exito --
     * reprocesando: score sobrescrito con otro scoredAt, un FraudCase duplicado con otro
     * caseId, un segundo evento publicado (dos investigaciones abiertas por el mismo
     * hecho). Reservando el turno primero, esa ventana no existe: mientras un hilo esta
     * dentro, cualquier otro con el mismo transactionId sale de inmediato sin tocar nada.
     */
    public void procesarTransaccion(Transaction transaction) {
        String transactionId = transaction.transactionId();

        if (!transaccionesEnProceso.add(transactionId)) {
            return; // otro hilo (evento in-process o poller) ya esta procesando esta transaccion
        }

        try {
            TransactionScore score = obtenerOCalcularScore(transaction, transactionId);

            if (score.score() > score.threshold() && fraudCaseRepository.findByTransactionId(transactionId).isEmpty()) {
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

    /**
     * Reutiliza el score ya guardado si existe (un reintento nunca debe recalcular ni
     * reemplazar un score existente, cambiando su scoredAt) y solo lo calcula de nuevo
     * cuando de verdad es la primera vez que se procesa esta transaccion.
     */
    private TransactionScore obtenerOCalcularScore(Transaction transaction, String transactionId) {
        Optional<TransactionScore> scoreExistente = scoreRepository.findByTransactionId(transactionId);
        if (scoreExistente.isPresent()) {
            return scoreExistente.get();
        }

        List<Transaction> history = transactionRepository.findByAccountId(transaction.accountId()).stream()
                .filter(item -> !item.transactionId().equals(transactionId))
                .toList();

        TransactionScore score = scoringEngine.score(transaction, history);
        scoreRepository.save(score);
        return score;
    }

    public RiesgoResponse obtenerRiesgo(String transactionId) {
        return scoreRepository.findByTransactionId(transactionId)
                .map(score -> RiesgoResponse.of(score, fraudCaseRepository.findByTransactionId(transactionId)))
                .orElseGet(() -> RiesgoResponse.pending(transactionId));
    }

    public RiesgoResponse updateFraudCaseStatus(String transactionId, String status) {
        transactionRepository.findById(transactionId)
                .orElseThrow(() -> new com.ingesta.exception.TransactionNotFoundException(transactionId));

        FraudCase fraudCase = fraudCaseRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new com.ingesta.exception.FraudCaseNotFoundException(transactionId));

        FraudCase updatedCase = new FraudCase(
                fraudCase.caseId(),
                fraudCase.transactionId(),
                fraudCase.score(),
                status,
                fraudCase.openedAt(),
                fraudCase.activations()
        );
        fraudCaseRepository.save(updatedCase);
        return obtenerRiesgo(transactionId);
    }

    public List<RiesgoResponse> listarScores() {
        return scoreRepository.findAll().stream()
                .map(score -> RiesgoResponse.of(score, fraudCaseRepository.findByTransactionId(score.transactionId())))
                .toList();
    }
}