package com.ingesta.service;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.ingesta.exception.EvidenciaInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * VULN 1 (CRITICO, IDOR): con el esquema viejo el prefijo de aislamiento era
 * "tx_" + transactionId + "_", por simple concatenacion. El transactionId lo elige el
 * cliente (sin @Pattern en TransactionRequest), asi que un atacante podia crear la
 * transaccion "A" y la transaccion "A_B": el prefijo de "A" ("tx_A_") es un prefijo
 * LITERAL del prefijo de "A_B" ("tx_A_B_"), y como listEvidencias/validateBlobName solo
 * comprobaban String.startsWith(prefix), la transaccion "A" podia listar y descargar las
 * evidencias (documentos de identidad) de "A_B".
 *
 * El fix (ver EvidenciaService.blobPrefix) codifica el transactionId en Base64 URL-safe
 * antes de anadir el separador "/" de carpeta: como ese alfabeto nunca produce "/", el
 * "/" que se agrega es siempre el unico limite de carpeta, y dos transactionId distintos
 * ya no pueden generar un prefijo que sea, a su vez, prefijo de otro.
 *
 * Estas pruebas fabrican los blobs llamando a cargarEvidenciaSegura (la propia API
 * publica del servicio), para no reimplementar la logica de blobPrefix en el test.
 */
@ExtendWith(MockitoExtension.class)
class EvidenciaServiceIdorTest {

    private static final byte[] PDF_CONTENT = {0x25, 0x50, 0x44, 0x46, 0x0A};

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    @Test
    void laTransaccionAYaNoVeNiDescargaLasEvidenciasDeLaTransaccionAB() throws Exception {
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);

        EvidenciaService service = new EvidenciaService(containerClient);

        String blobDeA = service.cargarEvidenciaSegura(
                "A", new ByteArrayInputStream(PDF_CONTENT), PDF_CONTENT.length);
        String blobDeAB = service.cargarEvidenciaSegura(
                "A_B", new ByteArrayInputStream(PDF_CONTENT), PDF_CONTENT.length);

        // Antes del fix, con el esquema "tx_" + id + "_", blobDeAB SIEMPRE empezaba por
        // "tx_A_" (el prefijo de la transaccion "A"). Con el esquema nuevo (carpeta en
        // base64url del transactionId) los prefijos son necesariamente distintos.
        assertTrue(blobDeA.startsWith(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("A".getBytes()) + "/"));
        assertTrue(blobDeAB.startsWith(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("A_B".getBytes()) + "/"));

        when(containerClient.listBlobs()).thenReturn(pagedIterableCon(blobDeA, blobDeAB));

        List<String> evidenciasDeA = service.listEvidencias("A");
        List<String> evidenciasDeAB = service.listEvidencias("A_B");

        assertEquals(List.of(blobDeA), evidenciasDeA);
        assertEquals(List.of(blobDeAB), evidenciasDeAB);
        assertTrue(evidenciasDeA.stream().noneMatch(nombre -> nombre.equals(blobDeAB)));

        // Descarga cruzada: la transaccion "A" pidiendo explicitamente el blob de "A_B"
        // debe ser rechazada por validateBlobName antes de tocar el blob real.
        assertThrows(EvidenciaInvalidaException.class,
                () -> service.descargarEvidencia("A", blobDeAB));
    }

    @Test
    void unSufijoConBarraTrasElPrefijoSigueSiendoRechazado() {
        // La mitigacion de directory traversal (un "/" adicional despues del prefijo de
        // carpeta) debe seguir funcionando igual que antes del fix.
        EvidenciaService service = new EvidenciaService(containerClient);
        String prefijoA = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("A".getBytes()) + "/";

        assertThrows(EvidenciaInvalidaException.class,
                () -> service.descargarEvidencia("A", prefijoA + "../secreto.pdf"));
        assertThrows(EvidenciaInvalidaException.class,
                () -> service.descargarEvidencia("A", prefijoA + "sub/otro.pdf"));
    }

    private PagedIterable<BlobItem> pagedIterableCon(String... nombres) {
        List<BlobItem> items = List.of(nombres).stream()
                .map(nombre -> new BlobItem().setName(nombre))
                .toList();
        PagedResponseBase<Void, BlobItem> pagedResponse =
                new PagedResponseBase<>(null, 200, new HttpHeaders(), items, null, null);
        return new PagedIterable<>(() -> pagedResponse);
    }
}
