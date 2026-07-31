package com.ingesta.service;

import com.ingesta.model.FraudCase;
import com.ingesta.model.Transaction;
import com.ingesta.model.TransactionScore;
import com.ingesta.repository.FraudCaseRepository;
import com.ingesta.repository.InMemoryFraudCaseRepository;
import com.ingesta.repository.InMemoryTransactionScoreRepository;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.repository.TransactionScoreRepository;
import com.ingesta.testsupport.TransaccionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias puras (sin Spring, sin credenciales de Azure) de
 * TransactionScoringService.procesarTransaccion, el punto donde disparan tanto el evento
 * in-process como TransactionQueuePoller.
 *
 * Cubre dos bugs de perdida de datos:
 * - BUG 2: un fallo parcial entre "guardar el score" y "crear/publicar el caso de
 *   fraude" no debe perder el caso para siempre en el reintento.
 * - BUG 3: una carrera entre dos hilos para la misma transaccion no debe procesarla
 *   dos veces (score/caso/evento duplicados).
 */
class TransactionScoringServiceTest {

    private static final Clock RELOJ_FIJO = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC);

    private TransactionRepository transactionRepository;
    private TransactionScoreRepository scoreRepository;
    private FraudCaseRepository fraudCaseRepository;
    private TransactionScoringEngine scoringEngine;
    private FraudCaseEventPublisher fraudCaseEventPublisher;
    private FraudAlertEmailPublisher fraudAlertEmailPublisher;
    private TransactionScoringService service;

    @BeforeEach
    void prepararColaboradoresMockeados() {
        transactionRepository = mock(TransactionRepository.class);
        scoreRepository = mock(TransactionScoreRepository.class);
        fraudCaseRepository = mock(FraudCaseRepository.class);
        scoringEngine = mock(TransactionScoringEngine.class);
        fraudCaseEventPublisher = mock(FraudCaseEventPublisher.class);
        fraudAlertEmailPublisher = mock(FraudAlertEmailPublisher.class);

        service = new TransactionScoringService(
                transactionRepository, scoreRepository, fraudCaseRepository,
                scoringEngine, fraudCaseEventPublisher, fraudAlertEmailPublisher, RELOJ_FIJO);

        when(transactionRepository.findByAccountId(any())).thenReturn(List.of());
    }

    private TransactionScore scoreDeAltoRiesgo(String transactionId) {
        return new TransactionScore(transactionId, 90, 60, Instant.now(RELOJ_FIJO), List.of());
    }

    @Test
    void unFalloAlCrearElCasoDeFraudeNoImpideQueElReintentoLoComplete() {
        // BUG 2: antes de la correccion, la guarda de idempotencia era solo
        // "existe un score" -- si el primer intento fallaba justo entre guardar el
        // score y crear/publicar el caso, el reintento veia el score ya guardado,
        // cortaba de inmediato y el caso de fraude jamas se creaba ni se publicaba.
        Transaction transaccion = TransaccionFixture.una().conId("tx-fallo-parcial").construir();
        TransactionScore score = scoreDeAltoRiesgo("tx-fallo-parcial");

        when(scoringEngine.score(any(), any())).thenReturn(score);

        // Primer intento: no hay score ni caso todavia, pero guardar el caso falla
        // (simula BD/Cosmos caida a mitad de proceso).
        when(scoreRepository.findByTransactionId("tx-fallo-parcial")).thenReturn(Optional.empty());
        when(fraudCaseRepository.findByTransactionId("tx-fallo-parcial")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Cosmos no disponible")).when(fraudCaseRepository).save(any());

        assertThrows(RuntimeException.class, () -> service.procesarTransaccion(transaccion));

        // El score SI quedo guardado (eso paso antes del fallo)...
        verify(scoreRepository, times(1)).save(score);
        // ...pero el caso de fraude NO se publico, porque save() exploto antes.
        verify(fraudCaseEventPublisher, never()).publicarCasoFraude(any());

        // Reintento (mismo mensaje reentregado, o el evento in-process que vuelve a
        // dispararse): ahora el score YA existe, y esta vez fraudCaseRepository.save
        // funciona con normalidad.
        when(scoreRepository.findByTransactionId("tx-fallo-parcial")).thenReturn(Optional.of(score));
        doNothing().when(fraudCaseRepository).save(any());

        service.procesarTransaccion(transaccion);

        // El caso de fraude SI se crea y se publica en el reintento: el trabajo
        // pendiente se completa en vez de saltarse. save() se invoco 2 veces en total
        // (la del primer intento, que exploto, y la del reintento, que si funciono);
        // lo que de verdad demuestra que el bug esta corregido es que la publicacion
        // SI ocurre esta vez, algo que antes de la correccion nunca pasaba.
        verify(fraudCaseRepository, times(2)).save(any(FraudCase.class));
        verify(fraudCaseEventPublisher, times(1)).publicarCasoFraude(any(FraudCase.class));
        // El score no se recalculo en el reintento (mismo objeto, un solo llamado al motor).
        verify(scoringEngine, times(1)).score(any(), any());
    }

    @Test
    void siElScoreYElCasoYaExistenElReintentoNoHaceNada() {
        // Caso sano de idempotencia: no hay nada pendiente, el reintento debe ser un no-op.
        Transaction transaccion = TransaccionFixture.una().conId("tx-completa").construir();
        TransactionScore score = scoreDeAltoRiesgo("tx-completa");
        FraudCase casoExistente = new FraudCase("case-1", "tx-completa", 90, "ABIERTO", Instant.now(RELOJ_FIJO), List.of());

        when(scoreRepository.findByTransactionId("tx-completa")).thenReturn(Optional.of(score));
        when(fraudCaseRepository.findByTransactionId("tx-completa")).thenReturn(Optional.of(casoExistente));

        service.procesarTransaccion(transaccion);

        verify(scoringEngine, never()).score(any(), any());
        verify(scoreRepository, never()).save(any());
        verify(fraudCaseRepository, never()).save(any());
        verify(fraudCaseEventPublisher, never()).publicarCasoFraude(any());
    }

    @Test
    void dosHilosConcurrentesParaLaMismaTransaccionSoloProcesanUnaVez() throws Exception {
        // BUG 3: reproduce la carrera de forma deterministica con CountDownLatch. El
        // hilo A entra primero (consigue el "add" de la guarda) y queda bloqueado a
        // mitad de proceso (dentro de findByAccountId); mientras tanto, el hilo B
        // intenta procesar la MISMA transaccion. Con la correccion (add() reservado
        // antes de leer nada), B debe salir de inmediato sin tocar ningun repositorio.
        // Se usan los repositorios en memoria reales (no mocks) para verificar el
        // estado final tal como quedaria en produccion, no solo el numero de llamadas.
        Transaction transaccion = TransaccionFixture.una().conId("tx-concurrente").construir();
        TransactionScoreRepository scoreRepoReal = new InMemoryTransactionScoreRepository();
        FraudCaseRepository fraudCaseRepoReal = new InMemoryFraudCaseRepository();

        CountDownLatch hiloADentroDelProceso = new CountDownLatch(1);
        CountDownLatch liberarHiloA = new CountDownLatch(1);

        TransactionRepository transactionRepoConDemora = mock(TransactionRepository.class);
        when(transactionRepoConDemora.findByAccountId(any())).thenAnswer(invocation -> {
            hiloADentroDelProceso.countDown();
            // Simula que el hilo A esta "a mitad de proceso" (p.ej. leyendo el
            // historial en Cosmos) mientras el hilo B intenta colarse.
            assertTrue(liberarHiloA.await(5, TimeUnit.SECONDS), "El test no debio bloquearse esperando la liberacion");
            return List.of();
        });

        TransactionScoringEngine motorAltoRiesgo = mock(TransactionScoringEngine.class);
        when(motorAltoRiesgo.score(any(), any())).thenReturn(scoreDeAltoRiesgo("tx-concurrente"));

        TransactionScoringService servicioConcurrente = new TransactionScoringService(
                transactionRepoConDemora, scoreRepoReal, fraudCaseRepoReal,
                motorAltoRiesgo, fraudCaseEventPublisher, fraudAlertEmailPublisher, RELOJ_FIJO);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> hiloA = executor.submit(() -> servicioConcurrente.procesarTransaccion(transaccion));

            assertTrue(hiloADentroDelProceso.await(5, TimeUnit.SECONDS),
                    "El hilo A debio entrar a la seccion critica antes de continuar el test");

            // Hilo B: misma transaccion, mientras A todavia esta dentro. Con el orden
            // corregido (add primero), esto debe ser un no-op inmediato.
            servicioConcurrente.procesarTransaccion(transaccion);

            // Prueba directa de que B no hizo nada: en este punto A todavia no ha
            // guardado el score (sigue bloqueado en el mock), asi que si B hubiera
            // avanzado, tampoco habria nada guardado por B -- lo relevante es que el
            // motor de scoring no se invoco una segunda vez.
            assertTrue(scoreRepoReal.findByTransactionId("tx-concurrente").isEmpty(),
                    "El score no debe existir todavia: A sigue bloqueado y B no debio procesar nada");

            liberarHiloA.countDown();
            hiloA.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Estado final: exactamente un score, un caso de fraude y un evento publicado.
        assertTrue(scoreRepoReal.findByTransactionId("tx-concurrente").isPresent());
        assertTrue(fraudCaseRepoReal.findByTransactionId("tx-concurrente").isPresent());
        verify(motorAltoRiesgo, times(1)).score(any(), any());
        verify(fraudCaseEventPublisher, times(1)).publicarCasoFraude(any());
    }
}
