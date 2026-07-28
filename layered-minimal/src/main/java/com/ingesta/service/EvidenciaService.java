package com.ingesta.service;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.ingesta.dto.EvidenciaDescargada;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
public class EvidenciaService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 Megabytes en Bytes
    private static final byte[] MAGIC_PDF = {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46}; // %PDF
    private static final byte[] MAGIC_PNG = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47}; // \x89PNG

    private final BlobContainerClient containerClient;

    public EvidenciaService(
            @Value("${azure.storage.account-name}") String accountName,
            @Value("${azure.storage.container-name}") String containerName) {
        // Conexion sin claves usando Identidad Gestionada / az login (RBAC de Azure)
        this.containerClient = new BlobServiceClientBuilder()
                .endpoint("https://" + accountName + ".blob.core.windows.net")
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient()
                .getBlobContainerClient(containerName);
    }

    public String cargarEvidenciaSegura(String transactionId, InputStream fileStream, long fileSize) throws IOException {

        // 1. CONTROL DE RIESGO: validar limite estricto de tamano de archivo (evita DoS)
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El archivo excede el limite permitido de 5 Megabytes.");
        }

        BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
        bufferedStream.mark(4);

        byte[] firstFourBytes = new byte[4];
        int bytesRead = bufferedStream.read(firstFourBytes, 0, 4);
        bufferedStream.reset();

        if (bytesRead < 4) {
            throw new IllegalArgumentException("Archivo corrupto o demasiado pequeno.");
        }

        // 2. VALIDACION DE CONTENIDO REAL: comprobar magic numbers, no la extension declarada por el cliente
        String extension;
        if (Arrays.equals(firstFourBytes, MAGIC_PDF)) {
            extension = "pdf";
        } else if (Arrays.equals(firstFourBytes, MAGIC_PNG)) {
            extension = "png";
        } else {
            throw new IllegalArgumentException("Tipo de archivo invalido. Solo se admiten PDFs o imagenes PNG reales.");
        }

        // 3. MITIGACION DE ATAQUES: el nombre original del archivo se descarta por completo
        // y se genera un nombre del lado del servidor, evitando directory traversal y sobreescrituras.
        String safeBlobName = "tx_" + transactionId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension;

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
            throw new IllegalArgumentException("Evidencia no encontrada para la transaccion indicada.");
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
        if (blobName == null || !blobName.startsWith(prefix) || blobName.contains("/") || blobName.contains("\\") || blobName.contains("..")) {
            throw new IllegalArgumentException("Nombre de evidencia invalido para la transaccion indicada.");
        }
    }

    private String blobPrefix(String transactionId) {
        return "tx_" + transactionId + "_";
    }
}
