package com.ingesta.service;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.ingesta.model.DatosDocumento;
import com.ingesta.repository.DatosDocumentoRepository;
import com.ingesta.repository.InMemoryDatosDocumentoRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de la extraccion local (PDFBox + regex), la alternativa a
 * AzureDocumentIntelligenceService cuando Document Intelligence no esta disponible en
 * la suscripcion. Cubre el mismo requisito de manejo de fallos: documento sin texto
 * (imagen escaneada o formato no soportado, como un PNG), documento con texto pero sin
 * datos identificables, y un PDF corrupto -- en los tres casos debe quedar FALLIDO
 * consultable y notificar al analista, igual que en el caso exitoso.
 */
@ExtendWith(MockitoExtension.class)
class LocalPdfDocumentProcessingServiceTest {

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    @Mock
    private BinaryData binaryData;

    @Mock
    private DocumentoProcesadoEventPublisher eventPublisher;

    private final DatosDocumentoRepository repository = new InMemoryDatosDocumentoRepository();

    @Test
    void extraeNombreNumeroIdentificacionYFechasDeUnPdfConTexto() throws Exception {
        byte[] pdf = generarPdfConTexto(
                "Nombre: Juan Perez",
                "Cedula: 123456789",
                "Fecha de nacimiento: 01/01/1990");

        when(containerClient.getBlobClient("blob-1")).thenReturn(blobClient);
        when(blobClient.downloadContent()).thenReturn(binaryData);
        when(binaryData.toBytes()).thenReturn(pdf);

        LocalPdfDocumentProcessingService service =
                new LocalPdfDocumentProcessingService(containerClient, repository, eventPublisher);
        service.extraerYAdjuntar("tx-1", "blob-1");

        DatosDocumento datos = repository.findByTransactionId("tx-1").orElseThrow();
        assertEquals(DatosDocumento.EstadoProcesamiento.COMPLETADO, datos.estado());
        assertEquals("Juan Perez", datos.nombre());
        assertEquals("123456789", datos.numeroIdentificacion());
        assertEquals(LocalDate.of(1990, 1, 1), datos.fechas().get("fecha_1"));
        verify(eventPublisher, times(1)).notificarResultado(datos);
    }

    @Test
    void quedaConsultableComoFallidoSiElPdfNoTieneTextoExtraible() throws Exception {
        byte[] pdfSinTexto = generarPdfConTexto();

        when(containerClient.getBlobClient("blob-2")).thenReturn(blobClient);
        when(blobClient.downloadContent()).thenReturn(binaryData);
        when(binaryData.toBytes()).thenReturn(pdfSinTexto);

        LocalPdfDocumentProcessingService service =
                new LocalPdfDocumentProcessingService(containerClient, repository, eventPublisher);
        service.extraerYAdjuntar("tx-2", "blob-2");

        DatosDocumento datos = repository.findByTransactionId("tx-2").orElseThrow();
        assertEquals(DatosDocumento.EstadoProcesamiento.FALLIDO, datos.estado());
        assertTrue(datos.motivoFallo() != null && !datos.motivoFallo().isBlank());
        verify(eventPublisher, times(1)).notificarResultado(datos);
    }

    @Test
    void quedaConsultableComoFallidoSiElArchivoNoEsUnPdfValido() {
        // Simula un PNG (u otro formato inesperado): no es un PDF real, PDFBox no puede leerlo.
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        when(containerClient.getBlobClient("blob-3")).thenReturn(blobClient);
        when(blobClient.downloadContent()).thenReturn(binaryData);
        when(binaryData.toBytes()).thenReturn(png);

        LocalPdfDocumentProcessingService service =
                new LocalPdfDocumentProcessingService(containerClient, repository, eventPublisher);
        service.extraerYAdjuntar("tx-3", "blob-3");

        DatosDocumento datos = repository.findByTransactionId("tx-3").orElseThrow();
        assertEquals(DatosDocumento.EstadoProcesamiento.FALLIDO, datos.estado());
        assertTrue(datos.motivoFallo() != null && !datos.motivoFallo().isBlank());
        verify(eventPublisher, times(1)).notificarResultado(datos);
    }

    private byte[] generarPdfConTexto(String... lineas) throws Exception {
        try (PDDocument documento = new PDDocument()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);

            if (lineas.length > 0) {
                try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                    contenido.beginText();
                    contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contenido.newLineAtOffset(50, 700);
                    for (String linea : lineas) {
                        contenido.showText(linea);
                        contenido.newLineAtOffset(0, -20);
                    }
                    contenido.endText();
                }
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }
}
