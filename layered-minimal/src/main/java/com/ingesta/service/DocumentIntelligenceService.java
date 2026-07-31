package com.ingesta.service;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions;
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.AnalyzedDocument;
import com.azure.ai.documentintelligence.models.DocumentField;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobContainerClient;
import com.ingesta.model.DatosDocumento;
import com.ingesta.repository.DatosDocumentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Servicio de reconocimiento documental: al cargar un documento en el contenedor de
 * evidencias, extrae datos estructurados (nombre, numero de identificacion, fechas) con
 * Azure AI Document Intelligence y los adjunta a la transaccion/caso correspondiente.
 *
 * Un documento ilegible, incompleto, corrupto o de formato inesperado no debe interrumpir
 * el flujo: en cualquiera de esos casos se guarda igual un resultado FALLIDO (consultable
 * via el repositorio/endpoint, en vez de desaparecer sin dejar rastro) y se notifica al
 * equipo analitico, igual que en el caso exitoso.
 */
@Service
public class DocumentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceService.class);
    private static final String MODELO_ID_DOCUMENTO = "prebuilt-idDocument";

    private final DocumentIntelligenceClient client;
    private final BlobContainerClient containerClient;
    private final DatosDocumentoRepository repository;
    private final DocumentoProcesadoEventPublisher eventPublisher;

    public DocumentIntelligenceService(
            DocumentIntelligenceClient documentIntelligenceClient,
            BlobContainerClient evidenciasContainerClient,
            DatosDocumentoRepository repository,
            DocumentoProcesadoEventPublisher eventPublisher) {
        this.client = documentIntelligenceClient;
        this.containerClient = evidenciasContainerClient;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Se ejecuta en un hilo aparte (eventoIngestaExecutor): la subida de la evidencia ya
     * respondio al cliente antes de que esto corra. Sea cual sea el resultado (exito o
     * fallo), siempre queda un DatosDocumento consultable y una notificacion al analista;
     * ningun escenario deja el documento en un limbo indistinguible de "aun no procesado".
     */
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

    private DatosDocumento analizar(String transactionId, String blobName) {
        byte[] contenido = containerClient.getBlobClient(blobName).downloadContent().toBytes();

        SyncPoller<AnalyzeOperationDetails, AnalyzeResult> poller =
                client.beginAnalyzeDocument(MODELO_ID_DOCUMENTO, new AnalyzeDocumentOptions(contenido));
        AnalyzeResult resultado = poller.getFinalResult();

        List<AnalyzedDocument> documentos = resultado.getDocuments();
        if (documentos.isEmpty()) {
            log.warn("El reconocimiento documental no identifico datos estructurados en {} (transaccion {})",
                    blobName, transactionId);
            return DatosDocumento.fallido(
                    transactionId, blobName,
                    "El servicio no identifico datos estructurados en el documento (ilegible, incompleto o de formato inesperado).",
                    Instant.now());
        }

        Map<String, DocumentField> campos = documentos.get(0).getFields();

        String nombre = Stream.of(valorTexto(campos, "FirstName"), valorTexto(campos, "LastName"))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        String numeroIdentificacion = valorTexto(campos, "DocumentNumber");

        Map<String, LocalDate> fechas = new LinkedHashMap<>();
        agregarSiNoEsNulo(fechas, "nacimiento", valorFecha(campos, "DateOfBirth"));
        agregarSiNoEsNulo(fechas, "vencimiento", valorFecha(campos, "DateOfExpiration"));

        return DatosDocumento.completado(
                transactionId, blobName, nombre.isBlank() ? null : nombre, numeroIdentificacion, fechas, Instant.now());
    }

    /**
     * VULN 5 (MEDIO, fuga de informacion interna): antes se concatenaba ex.getMessage()
     * -- el mensaje crudo del SDK de Azure -- en motivoFallo, que se guarda en
     * DatosDocumento y se devuelve tal cual por la API publica GET /{id}/datos-documento.
     * Las excepciones de SDKs de nube suelen incluir endpoints, nombres de recursos,
     * codigos de error internos, etc. (p.ej. "https://.../cognitiveservices...", nombres
     * de contenedores), informacion que un atacante puede usar para reconocimiento de la
     * infraestructura.
     *
     * FIX: motivoFallo pasa a ser SIEMPRE un mensaje de dominio fijo, sin datos del SDK.
     * El detalle real de la excepcion ya se registra completo (mensaje + stacktrace) en
     * el log.error de extraerYAdjuntar antes de llegar aqui -- ahi es donde debe
     * consultarse para depurar, no en la respuesta HTTP publica.
     */
    private String motivoLegible(Exception ex) {
        return "No se pudo procesar el documento (corrupto o formato inesperado). "
                + "El detalle tecnico quedo registrado en los logs internos.";
    }

    private void agregarSiNoEsNulo(Map<String, LocalDate> fechas, String clave, LocalDate valor) {
        if (valor != null) {
            fechas.put(clave, valor);
        }
    }

    private String valorTexto(Map<String, DocumentField> campos, String nombreCampo) {
        DocumentField campo = campos.get(nombreCampo);
        return campo != null ? campo.getValueString() : null;
    }

    private LocalDate valorFecha(Map<String, DocumentField> campos, String nombreCampo) {
        DocumentField campo = campos.get(nombreCampo);
        return campo != null ? campo.getValueDate() : null;
    }
}
