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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reconocimiento documental extrayendo datos estructurados (nombre, numero de
 * identificacion, fechas) con Azure AI Document Intelligence (modelo prebuilt-idDocument).
 */
public class AzureDocumentIntelligenceService extends AbstractReconocimientoDocumentalService {

    private static final Logger log = LoggerFactory.getLogger(AzureDocumentIntelligenceService.class);
    private static final String MODELO_ID_DOCUMENTO = "prebuilt-idDocument";

    private final DocumentIntelligenceClient client;
    private final BlobContainerClient containerClient;

    public AzureDocumentIntelligenceService(
            DocumentIntelligenceClient documentIntelligenceClient,
            BlobContainerClient evidenciasContainerClient,
            DatosDocumentoRepository repository,
            DocumentoProcesadoEventPublisher eventPublisher) {
        super(repository, eventPublisher);
        this.client = documentIntelligenceClient;
        this.containerClient = evidenciasContainerClient;
    }

    @Override
    protected DatosDocumento analizar(String transactionId, String blobName) {
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
