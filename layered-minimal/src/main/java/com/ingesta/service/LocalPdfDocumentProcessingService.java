package com.ingesta.service;

import com.azure.storage.blob.BlobContainerClient;
import com.ingesta.model.DatosDocumento;
import com.ingesta.repository.DatosDocumentoRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconocimiento documental sin dependencias de Azure: extrae el texto del PDF con
 * Apache PDFBox (libreria pura Java) y busca nombre, numero de identificacion y fechas
 * con heuristicas de texto/expresiones regulares. Alternativa a
 * {@link AzureDocumentIntelligenceService} para cuando el servicio de Document
 * Intelligence no esta disponible en la suscripcion (ver reconocimiento.modo).
 *
 * Al no usar un modelo entrenado de documentos de identidad, la precision es menor:
 * depende de que el documento tenga capa de texto real (no una imagen escaneada) y de
 * que use rotulos reconocibles (Nombre/Name, Cedula/Documento/ID, fechas en formato
 * dd/mm/aaaa o aaaa-mm-dd). Un PNG, por ejemplo, nunca produce texto extraible aqui;
 * eso se trata igual que cualquier otro documento sin datos identificables: queda
 * FALLIDO y consultable, no rompe el flujo.
 */
public class LocalPdfDocumentProcessingService extends AbstractReconocimientoDocumentalService {

    private static final Logger log = LoggerFactory.getLogger(LocalPdfDocumentProcessingService.class);

    // [ \t] en vez de \s: el nombre no debe cruzar saltos de linea hacia el siguiente rotulo.
    private static final Pattern PATRON_NOMBRE = Pattern.compile(
            "(?i)nombres?[ \\t]*[:\\-][ \\t]*([\\p{Lu}][\\p{L}]+(?:[ \\t]+[\\p{Lu}][\\p{L}]+){0,3})");
    private static final Pattern PATRON_ID = Pattern.compile(
            "(?i)(?:c\\.?c\\.?|cedula|documento|identificaci[oó]n|id)[ \\t]*(?:de identidad)?[ \\t]*[:\\-nNo\\.]*[ \\t]*([0-9]{5,12})");
    private static final Pattern PATRON_FECHA_DMY = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern PATRON_FECHA_YMD = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final int MAX_FECHAS = 5;

    private final BlobContainerClient containerClient;

    public LocalPdfDocumentProcessingService(
            BlobContainerClient evidenciasContainerClient,
            DatosDocumentoRepository repository,
            DocumentoProcesadoEventPublisher eventPublisher) {
        super(repository, eventPublisher);
        this.containerClient = evidenciasContainerClient;
    }

    @Override
    protected DatosDocumento analizar(String transactionId, String blobName) throws Exception {
        byte[] contenido = containerClient.getBlobClient(blobName).downloadContent().toBytes();

        String texto;
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            texto = new PDFTextStripper().getText(documento);
        }

        if (texto == null || texto.isBlank()) {
            log.warn("El documento {} no tiene texto extraible (transaccion {})", blobName, transactionId);
            return DatosDocumento.fallido(
                    transactionId, blobName,
                    "El documento no contiene texto extraible (posible imagen escaneada sin capa de texto, "
                            + "o formato no soportado por la extraccion local).",
                    Instant.now());
        }

        String nombre = extraerNombre(texto);
        String numeroIdentificacion = extraerNumeroIdentificacion(texto);
        Map<String, LocalDate> fechas = extraerFechas(texto);

        if (nombre == null && numeroIdentificacion == null && fechas.isEmpty()) {
            log.warn("No se identifico nombre, ID ni fechas en el texto de {} (transaccion {})", blobName, transactionId);
            return DatosDocumento.fallido(
                    transactionId, blobName,
                    "No se pudo identificar nombre, numero de identificacion ni fechas en el texto extraido "
                            + "(documento incompleto o de formato inesperado).",
                    Instant.now());
        }

        return DatosDocumento.completado(transactionId, blobName, nombre, numeroIdentificacion, fechas, Instant.now());
    }

    private String extraerNombre(String texto) {
        Matcher m = PATRON_NOMBRE.matcher(texto);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extraerNumeroIdentificacion(String texto) {
        Matcher m = PATRON_ID.matcher(texto);
        return m.find() ? m.group(1) : null;
    }

    private Map<String, LocalDate> extraerFechas(String texto) {
        Map<String, LocalDate> fechas = new LinkedHashMap<>();
        int contador = 1;

        Matcher dmy = PATRON_FECHA_DMY.matcher(texto);
        while (contador <= MAX_FECHAS && dmy.find()) {
            LocalDate fecha = intentarFecha(dmy.group(3), dmy.group(2), dmy.group(1));
            if (fecha != null) {
                fechas.put("fecha_" + contador++, fecha);
            }
        }

        Matcher ymd = PATRON_FECHA_YMD.matcher(texto);
        while (contador <= MAX_FECHAS && ymd.find()) {
            LocalDate fecha = intentarFecha(ymd.group(1), ymd.group(2), ymd.group(3));
            if (fecha != null) {
                fechas.put("fecha_" + contador++, fecha);
            }
        }

        return fechas;
    }

    private LocalDate intentarFecha(String anio, String mes, String dia) {
        try {
            return LocalDate.of(Integer.parseInt(anio), Integer.parseInt(mes), Integer.parseInt(dia));
        } catch (NumberFormatException | DateTimeException ex) {
            return null;
        }
    }
}
