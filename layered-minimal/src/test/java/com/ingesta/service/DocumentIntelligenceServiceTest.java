package com.ingesta.service;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions;
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.AnalyzedDocument;
import com.azure.ai.documentintelligence.models.DocumentField;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.ingesta.model.DatosDocumento;
import com.ingesta.repository.DatosDocumentoRepository;
import com.ingesta.repository.InMemoryDatosDocumentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria del mapeo de campos extraidos (nombre, ID, fechas) desde la respuesta
 * de Azure AI Document Intelligence, sin depender de credenciales ni del servicio real
 * (no existe un emulador local para Document Intelligence, a diferencia de Storage/Azurite).
 */
@ExtendWith(MockitoExtension.class)
class DocumentIntelligenceServiceTest {

    @Mock
    private DocumentIntelligenceClient client;

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    @SuppressWarnings("unchecked")
    private final SyncPoller<AnalyzeOperationDetails, AnalyzeResult> poller = mock(SyncPoller.class);

    @Mock
    private AnalyzeResult analyzeResult;

    @Mock
    private AnalyzedDocument analyzedDocument;

    @Mock
    private BinaryData binaryData;

    private final DatosDocumentoRepository repository = new InMemoryDatosDocumentoRepository();

    @Test
    void extraeNombreNumeroIdentificacionYFechasDelDocumento() {
        when(containerClient.getBlobClient("blob-1")).thenReturn(blobClient);
        when(blobClient.downloadContent()).thenReturn(binaryData);
        when(binaryData.toBytes()).thenReturn(new byte[] {1, 2, 3});

        when(client.beginAnalyzeDocument(eq("prebuilt-idDocument"), any(AnalyzeDocumentOptions.class)))
                .thenReturn(poller);
        when(poller.getFinalResult()).thenReturn(analyzeResult);
        when(analyzeResult.getDocuments()).thenReturn(List.of(analyzedDocument));

        Map<String, DocumentField> campos = new HashMap<>();
        campos.put("FirstName", campoTexto("Juan"));
        campos.put("LastName", campoTexto("Perez"));
        campos.put("DocumentNumber", campoTexto("CC123456"));
        campos.put("DateOfBirth", campoFecha(LocalDate.of(1990, 1, 1)));
        campos.put("DateOfExpiration", campoFecha(LocalDate.of(2030, 1, 1)));
        when(analyzedDocument.getFields()).thenReturn(campos);

        DocumentIntelligenceService service = new DocumentIntelligenceService(client, containerClient, repository);
        service.extraerYAdjuntar("tx-1", "blob-1");

        DatosDocumento datos = repository.findByTransactionId("tx-1").orElseThrow();
        assertEquals("Juan Perez", datos.nombre());
        assertEquals("CC123456", datos.numeroIdentificacion());
        assertEquals(LocalDate.of(1990, 1, 1), datos.fechas().get("nacimiento"));
        assertEquals(LocalDate.of(2030, 1, 1), datos.fechas().get("vencimiento"));
    }

    @Test
    void noAdjuntaNadaSiElServicioNoIdentificaNingunDocumento() {
        when(containerClient.getBlobClient("blob-2")).thenReturn(blobClient);
        when(blobClient.downloadContent()).thenReturn(binaryData);
        when(binaryData.toBytes()).thenReturn(new byte[] {1});

        when(client.beginAnalyzeDocument(eq("prebuilt-idDocument"), any(AnalyzeDocumentOptions.class)))
                .thenReturn(poller);
        when(poller.getFinalResult()).thenReturn(analyzeResult);
        when(analyzeResult.getDocuments()).thenReturn(List.of());

        DocumentIntelligenceService service = new DocumentIntelligenceService(client, containerClient, repository);
        service.extraerYAdjuntar("tx-2", "blob-2");

        assertTrue(repository.findByTransactionId("tx-2").isEmpty());
    }

    @Test
    void noPropagaExcepcionesSiFallaLaExtraccion() {
        when(containerClient.getBlobClient("blob-3")).thenThrow(new RuntimeException("fallo simulado de Azure"));

        DocumentIntelligenceService service = new DocumentIntelligenceService(client, containerClient, repository);

        service.extraerYAdjuntar("tx-3", "blob-3");

        assertTrue(repository.findByTransactionId("tx-3").isEmpty());
    }

    private DocumentField campoTexto(String valor) {
        DocumentField campo = mock(DocumentField.class);
        when(campo.getValueString()).thenReturn(valor);
        return campo;
    }

    private DocumentField campoFecha(LocalDate valor) {
        DocumentField campo = mock(DocumentField.class);
        when(campo.getValueDate()).thenReturn(valor);
        return campo;
    }
}
