package com.ingesta.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.ingesta.repository.FraudCaseRepository;
import com.ingesta.repository.InMemoryFraudCaseRepository;
import com.ingesta.repository.InMemoryTransactionRepository;
import com.ingesta.repository.InMemoryTransactionScoreRepository;
import com.ingesta.repository.TransactionRepository;
import com.ingesta.repository.TransactionScoreRepository;

/**
 * Deja constancia en el arranque de QUE implementacion de cada repositorio quedo activa.
 *
 * Las tres implementaciones en memoria son fallbacks silenciosos: si falta la variable de
 * entorno que activa Cosmos (INGESTA_COSMOS_ENABLED) o la que saca a la app del perfil
 * "local" (SPRING_PROFILES_ACTIVE), la aplicacion arranca sana, responde 202 a cada
 * ingesta y abre casos de fraude con normalidad -- solo que todo vive en un
 * ConcurrentHashMap que se vacia en el siguiente reinicio o replica. Es exactamente el
 * modo en que estuvo corriendo produccion: sin error, sin excepcion, y sin un solo
 * documento en Cosmos ni una fila en SQL.
 *
 * Fuera del perfil "local" eso nunca es lo que se quiere, asi que se registra en ERROR con
 * el nombre de la variable que falta. No se aborta el arranque a proposito: el App Service
 * ya tuvo problemas de startup probe por dependencias de datos lentas (ver
 * spring.datasource.hikari.initialization-fail-timeout en application.properties), y
 * degradar es preferible a un contenedor que no levanta. El ERROR queda visible en
 * Application Insights.
 */
@Component
public class PersistenceModeStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(PersistenceModeStartupCheck.class);

    private final Environment environment;
    private final TransactionRepository transactionRepository;
    private final TransactionScoreRepository scoreRepository;
    private final FraudCaseRepository fraudCaseRepository;

    public PersistenceModeStartupCheck(
            Environment environment,
            TransactionRepository transactionRepository,
            TransactionScoreRepository scoreRepository,
            FraudCaseRepository fraudCaseRepository) {
        this.environment = environment;
        this.transactionRepository = transactionRepository;
        this.scoreRepository = scoreRepository;
        this.fraudCaseRepository = fraudCaseRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificarModoDePersistencia() {
        log.info("Persistencia activa -> transacciones={}, scores={}, casos={}",
                nombreDeImplementacion(transactionRepository),
                nombreDeImplementacion(scoreRepository),
                nombreDeImplementacion(fraudCaseRepository));

        List<String> volatiles = new ArrayList<>();
        List<String> comoCorregirlo = new ArrayList<>();

        if (esEnMemoria(transactionRepository, InMemoryTransactionRepository.class)) {
            volatiles.add("transacciones");
        }
        if (esEnMemoria(scoreRepository, InMemoryTransactionScoreRepository.class)) {
            volatiles.add("scores");
        }
        if (!volatiles.isEmpty()) {
            comoCorregirlo.add("Cosmos apagado (spring.cloud.azure.cosmos.enabled)");
        }
        if (esEnMemoria(fraudCaseRepository, InMemoryFraudCaseRepository.class)) {
            volatiles.add("casos de fraude");
            comoCorregirlo.add("repositorio JPA inactivo (perfiles activos: "
                    + String.join(",", environment.getActiveProfiles()) + ")");
        }

        if (volatiles.isEmpty()) {
            return;
        }

        if (perfilLocalActivo()) {
            log.info("Perfil 'local': {} viven solo en memoria. Nada se persiste, es lo esperado en desarrollo.",
                    String.join(", ", volatiles));
            return;
        }

        log.error("PERSISTENCIA EN MEMORIA FUERA DEL PERFIL 'local': {} no se estan guardando en Azure ({}). "
                        + "La API respondera con normalidad pero los datos se perderan en el proximo reinicio "
                        + "o quedaran divididos entre replicas.",
                String.join(", ", volatiles), String.join("; ", comoCorregirlo));
    }

    private boolean perfilLocalActivo() {
        return Set.of(environment.getActiveProfiles()).contains("local");
    }

    /**
     * Spring Data registra un PersistenceExceptionTranslationPostProcessor que envuelve en
     * un proxy JDK a todo bean anotado con @Repository, asi que un `instanceof` contra la
     * clase concreta daria false aunque el bean SI sea el repositorio en memoria. Hay que
     * comparar contra la clase destino real del proxy.
     */
    private boolean esEnMemoria(Object repository, Class<?> implementacionEnMemoria) {
        return implementacionEnMemoria.isAssignableFrom(AopProxyUtils.ultimateTargetClass(repository));
    }

    private String nombreDeImplementacion(Object repository) {
        return AopProxyUtils.ultimateTargetClass(repository).getSimpleName();
    }
}
