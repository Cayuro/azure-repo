package com.ingesta.service;

import com.ingesta.model.DatosDocumento;
import com.ingesta.repository.DatosDocumentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;

/**
 * Protocolo comun de manejo de fallos para cualquier implementacion de reconocimiento
 * documental (ya sea con Azure AI Document Intelligence o con una libreria local): un
 * documento ilegible, incompleto, corrupto o de formato inesperado no debe interrumpir
 * el flujo. En todos los casos queda un DatosDocumento consultable (COMPLETADO o
 * FALLIDO, nunca "desaparecido") y se notifica al equipo analitico.
 *
 * Se comparte esta clase base -en vez de duplicar el mismo try/catch en cada
 * implementacion- justamente porque el requisito exige que el manejo de fallos sea
 * identico entre ambas: compartir el codigo es la unica forma de garantizarlo.
 */
public abstract class AbstractReconocimientoDocumentalService implements ReconocimientoDocumentalService {

    private static final Logger log = LoggerFactory.getLogger(AbstractReconocimientoDocumentalService.class);

    private final DatosDocumentoRepository repository;
    private final DocumentoProcesadoEventPublisher eventPublisher;

    protected AbstractReconocimientoDocumentalService(
            DatosDocumentoRepository repository, DocumentoProcesadoEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Se ejecuta en un hilo aparte (eventoIngestaExecutor): la subida de la evidencia ya
     * respondio al cliente antes de que esto corra.
     */
    @Override
    @Async("eventoIngestaExecutor")
    public void extraerYAdjuntar(String transactionId, String blobName) {
        DatosDocumento resultado;
        try {
            resultado = analizar(transactionId, blobName);
        } catch (Exception ex) {
            log.error("No se pudo extraer datos estructurados del documento {} de la transaccion {}",
                    blobName, transactionId, ex);
            resultado = DatosDocumento.fallido(transactionId, blobName, motivoLegible(ex), Instant.now());
        }

        try {
            repository.save(resultado);
            eventPublisher.notificarResultado(resultado);
        } catch (Exception ex) {
            log.error("No se pudo guardar/notificar el resultado del procesamiento documental de la transaccion {}",
                    transactionId, ex);
        }
    }

    /**
     * Analiza el documento y devuelve el resultado (COMPLETADO o FALLIDO). Puede lanzar
     * cualquier excepcion: la clase base la captura y la convierte en un resultado FALLIDO
     * consultable, no hace falta que cada implementacion repita ese manejo.
     */
    protected abstract DatosDocumento analizar(String transactionId, String blobName) throws Exception;

    protected String motivoLegible(Exception ex) {
        String mensaje = ex.getMessage();
        return "No se pudo procesar el documento (corrupto o formato inesperado): "
                + (mensaje != null && !mensaje.isBlank() ? mensaje : ex.getClass().getSimpleName());
    }
}
