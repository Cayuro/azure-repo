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
 */
@Service
public class DocumentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceService.class);
    private static final String MODELO_ID_DOCUMENTO = "prebuilt-idDocument";

    private final DocumentIntelligenceClient client;
    private final BlobContainerClient containerClient;
    private final DatosDocumentoRepository repository;

    public DocumentIntelligenceService(
            DocumentIntelligenceClient documentIntelligenceClient,
            BlobContainerClient evidenciasContainerClient,
            DatosDocumentoRepository repository) {
        this.client = documentIntelligenceClient;
        this.containerClient = evidenciasContainerClient;
        this.repository = repository;
    }

    /**
     * Se ejecuta en un hilo aparte (eventoIngestaExecutor): la subida de la evidencia ya
     * respondio al cliente antes de que esto corra. Cualquier fallo se registra sin
     * propagarse, para no afectar una respuesta que ya se envio.
     */
    @Async("eventoIngestaExecutor")
    public void extraerYAdjuntar(String transactionId, String blobName) {
        try {
            byte[] contenido = containerClient.getBlobClient(blobName).downloadContent().toBytes();

            SyncPoller<AnalyzeOperationDetails, AnalyzeResult> poller =
                    client.beginAnalyzeDocument(MODELO_ID_DOCUMENTO, new AnalyzeDocumentOptions(contenido));
            AnalyzeResult resultado = poller.getFinalResult();

            List<AnalyzedDocument> documentos = resultado.getDocuments();
            if (documentos.isEmpty()) {
                log.warn("El reconocimiento documental no identifico datos estructurados en {} (transaccion {})",
                        blobName, transactionId);
                return;
            }

            Map<String, DocumentField> campos = documentos.get(0).getFields();

            String nombre = Stream.of(valorTexto(campos, "FirstName"), valorTexto(campos, "LastName"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));
            String numeroIdentificacion = valorTexto(campos, "DocumentNumber");

            Map<String, LocalDate> fechas = new LinkedHashMap<>();
            agregarSiNoEsNulo(fechas, "nacimiento", valorFecha(campos, "DateOfBirth"));
            agregarSiNoEsNulo(fechas, "vencimiento", valorFecha(campos, "DateOfExpiration"));

            repository.save(new DatosDocumento(
                    transactionId, blobName, nombre.isBlank() ? null : nombre, numeroIdentificacion, fechas, Instant.now()));
        } catch (Exception ex) {
            log.error("No se pudo extraer datos estructurados del documento {} de la transaccion {}",
                    blobName, transactionId, ex);
        }
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
