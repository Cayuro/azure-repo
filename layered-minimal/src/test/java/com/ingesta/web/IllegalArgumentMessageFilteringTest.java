package com.ingesta.web;

import com.ingesta.config.RateLimitProperties;
import com.ingesta.controller.TransactionController;
import com.ingesta.exception.EvidenciaInvalidaException;
import com.ingesta.repository.DatosDocumentoRepository;
import com.ingesta.service.DocumentIntelligenceService;
import com.ingesta.service.EvidenciaService;
import com.ingesta.service.TransactionScoringService;
import com.ingesta.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG 5: handleIllegalArgument devolvia ex.getMessage() sin filtrar para CUALQUIER
 * IllegalArgumentException, incluidas las que puede lanzar el SDK de Azure (Cosmos, Blob
 * Storage) o MediaType.parseMediaType, exponiendo endpoints/nombres de contenedores al
 * cliente.
 *
 * La correccion introduce EvidenciaInvalidaException para los mensajes de negocio que el
 * frontend SI debe mostrar (limite de tamano, tipo de archivo, nombre/evidencia invalidos)
 * y deja que cualquier otra IllegalArgumentException (p.ej. del SDK de Azure) reciba un
 * mensaje generico.
 */
@WebMvcTest(controllers = TransactionController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
class IllegalArgumentMessageFilteringTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;
    @MockBean
    private EvidenciaService evidenciaService;
    @MockBean
    private TransactionScoringService transactionScoringService;
    @MockBean
    private DocumentIntelligenceService documentIntelligenceService;
    @MockBean
    private DatosDocumentoRepository datosDocumentoRepository;

    @Test
    void unaIllegalArgumentExceptionInesperadaNoFiltraSuMensaje() throws Exception {
        // Simula lo que podria escapar del SDK de Azure: un mensaje con detalles internos
        // (endpoint, nombre de contenedor) que NUNCA deben llegar al cliente.
        String detalleInterno = "CosmosException: endpoint https://mi-cuenta-cosmos.documents.azure.com:443/ inalcanzable, contenedor 'transacciones-prod'";
        when(transactionService.getById(anyString()))
                .thenThrow(new IllegalArgumentException(detalleInterno));

        mockMvc.perform(multipart("/api/v1/transactions/tx-1/evidencias")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La solicitud contiene un argumento invalido"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("documents.azure.com"))))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("transacciones-prod"))));
    }

    @Test
    void unMensajeDeNegocioDeEvidenciaSiSePropaga() throws Exception {
        // EvidenciaInvalidaException es la via explicita para mensajes que el frontend ya
        // muestra al usuario; su texto debe llegar intacto, sin el filtro generico.
        when(transactionService.getById(anyString())).thenReturn(null);
        when(evidenciaService.cargarEvidenciaSegura(anyString(), any(), anyLong()))
                .thenThrow(new EvidenciaInvalidaException("Tipo de archivo invalido. Solo se admiten PDFs o imagenes PNG reales."));

        mockMvc.perform(multipart("/api/v1/transactions/tx-1/evidencias")
                        .file(new MockMultipartFile("file", "doc.exe", "application/octet-stream", new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tipo de archivo invalido. Solo se admiten PDFs o imagenes PNG reales."));
    }
}
