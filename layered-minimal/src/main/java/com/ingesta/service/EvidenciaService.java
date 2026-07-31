package com.ingesta.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.ingesta.dto.EvidenciaDescargada;
import com.ingesta.exception.EvidenciaInvalidaException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
public class EvidenciaService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 Megabytes en Bytes
    private static final byte[] MAGIC_PDF = {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46}; // %PDF
    private static final byte[] MAGIC_PNG = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47}; // \x89PNG

    private final BlobContainerClient containerClient;

    public EvidenciaService(BlobContainerClient evidenciasContainerClient) {
        this.containerClient = evidenciasContainerClient;
    }

    public String cargarEvidenciaSegura(String transactionId, InputStream fileStream, long fileSize) throws IOException {

        // 1. CONTROL DE RIESGO: validar limite estricto de tamano de archivo (evita DoS)
        if (fileSize > MAX_FILE_SIZE) {
            throw new EvidenciaInvalidaException("El archivo excede el limite permitido de 5 Megabytes.");
        }

        BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
        bufferedStream.mark(4);

        byte[] firstFourBytes = new byte[4];
        int bytesRead = bufferedStream.read(firstFourBytes, 0, 4);
        bufferedStream.reset();

        if (bytesRead < 4) {
            throw new EvidenciaInvalidaException("Archivo corrupto o demasiado pequeno.");
        }

        // 2. VALIDACION DE CONTENIDO REAL: comprobar magic numbers, no la extension declarada por el cliente
        String extension;
        if (Arrays.equals(firstFourBytes, MAGIC_PDF)) {
            extension = "pdf";
        } else if (Arrays.equals(firstFourBytes, MAGIC_PNG)) {
            extension = "png";
        } else {
            throw new EvidenciaInvalidaException("Tipo de archivo invalido. Solo se admiten PDFs o imagenes PNG reales.");
        }

        // 3. MITIGACION DE ATAQUES: el nombre original del archivo se descarta por completo
        // y se genera un nombre del lado del servidor, evitando directory traversal y sobreescrituras.
        String safeBlobName = blobPrefix(transactionId) + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

        BlobClient blobClient = containerClient.getBlobClient(safeBlobName);
        blobClient.upload(bufferedStream, fileSize, true);

        return safeBlobName;
    }

    public List<String> listEvidencias(String transactionId) {
        String prefix = blobPrefix(transactionId);
        return StreamSupport.stream(containerClient.listBlobs().spliterator(), false)
                .map(BlobItem::getName)
                .filter(blobName -> blobName.startsWith(prefix))
                .sorted()
                .toList();
    }

    public EvidenciaDescargada descargarEvidencia(String transactionId, String blobName) throws IOException {
        validateBlobName(transactionId, blobName);

        BlobClient blobClient = containerClient.getBlobClient(blobName);
        if (!blobClient.exists()) {
            throw new EvidenciaInvalidaException("Evidencia no encontrada para la transaccion indicada.");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        blobClient.downloadStream(outputStream);

        BlobProperties properties = blobClient.getProperties();
        String contentType = properties.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = blobName.toLowerCase().endsWith(".png") ? "image/png" : "application/pdf";
        }

        return new EvidenciaDescargada(blobName, contentType, outputStream.toByteArray());
    }

    private void validateBlobName(String transactionId, String blobName) {
        String prefix = blobPrefix(transactionId);
        if (blobName == null || !blobName.startsWith(prefix)) {
            throw new EvidenciaInvalidaException("Nombre de evidencia invalido para la transaccion indicada.");
        }
        // El unico "/" legitimo en un blobName es el que ya consumio blobPrefix() como
        // separador de "carpeta". Cualquier "/", "\" o ".." adicional en lo que queda
        // (uuid + extension) es un intento de escapar de la carpeta de la transaccion
        // (directory traversal) y se rechaza igual que antes del fix.
        String sufijo = blobName.substring(prefix.length());
        if (sufijo.isEmpty() || sufijo.contains("/") || sufijo.contains("\\") || sufijo.contains("..")) {
            throw new EvidenciaInvalidaException("Nombre de evidencia invalido para la transaccion indicada.");
        }
    }

    /**
     * VULN 1 (CRITICO, IDOR): antes el prefijo era "tx_" + transactionId + "_", construido
     * por simple concatenacion de texto. transactionId lo elige libremente el cliente (ver
     * TransactionRequest, sin @Pattern), asi que un atacante podia crear una transaccion
     * "A" y otra "A_B": el prefijo de "A" ("tx_A_") es un prefijo LITERAL del prefijo de
     * "A_B" ("tx_A_B_"), y como listEvidencias/validateBlobName solo comprobaban
     * String.startsWith(prefix), la transaccion "A" podia listar y descargar las
     * evidencias (documentos de identidad) de "A_B". Fuga de datos personales entre
     * transacciones no relacionadas.
     *
     * FIX: se codifica transactionId en Base64 URL-safe antes de anadir el separador "/".
     * El alfabeto Base64 URL-safe (A-Z a-z 0-9 - _) nunca produce "/", asi que el "/" que
     * anadimos aqui SIEMPRE es el unico limite de "carpeta" dentro del blob name, sin
     * importar que caracteres traiga transactionId (incluida una "/" literal, que queda
     * codificada como texto en vez de actuar como separador). Dos transactionId distintos
     * ya no pueden producir un prefijo que sea, a su vez, prefijo de otro: la ambiguedad
     * que causaba el IDOR queda eliminada por construccion, no por una lista de casos
     * prohibidos.
     *
     * RETROCOMPATIBILIDAD: este cambio de esquema NO es retrocompatible. Las evidencias ya
     * subidas con el esquema viejo ("tx_<transactionId>_<uuid>.<ext>", sin carpeta) no
     * empiezan por el prefijo nuevo ("<transactionId-en-base64url>/") y por tanto dejan de
     * listarse y descargarse via la API para esa transaccion. No se pierden datos (los
     * blobs siguen en el contenedor), pero quedan "huerfanos" hasta que se migren: un job
     * de migracion deberia recorrer el contenedor, extraer el transactionId de cada nombre
     * con el patron viejo (regex "^tx_(.+)_[0-9a-f]{8}\\.(pdf|png)$") y copiar/renombrar
     * cada blob a blobPrefix(transactionId) + mismo sufijo. Se documenta aqui en vez de
     * ejecutarse automaticamente porque requiere acceso al contenedor real de produccion
     * y una ventana de mantenimiento coordinada con el equipo de datos.
     */
    private String blobPrefix(String transactionId) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(transactionId.getBytes(StandardCharsets.UTF_8))
                + "/";
    }
}
