package com.ingesta.web;

import com.ingesta.config.RateLimitProperties;
import com.ingesta.controller.TransactionController;
import com.ingesta.dto.RiesgoResponse;
import com.ingesta.repository.DatosDocumentoRepository;
import com.ingesta.service.DocumentIntelligenceService;
import com.ingesta.service.EvidenciaService;
import com.ingesta.service.TransactionScoringService;
import com.ingesta.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG 3: PUT /{transactionId}/status no llevaba @Valid ni UpdateFraudCaseStatusRequest
 * tenia anotaciones, asi que se aceptaba cualquier cadena arbitraria (o null) y se
 * persistia, corrompiendo el caso de fraude. Los cinco estados reales son
 * ABIERTO, EN_REVISION, REVISADO, CERRADO, PENDIENTE (ver
 * client/src/components/TransactionDetailCard.jsx, STATUS_OPTIONS).
 *
 * Se sigue el patron de WebLayerBootProbeTest: @WebMvcTest sobre el controller con los 5
 * colaboradores mockeados, sin necesidad de arrancar Cosmos/Azure.
 */
@WebMvcTest(controllers = TransactionController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
class UpdateFraudCaseStatusValidationTest {

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

    @BeforeEach
    void laTransaccionSiempreExiste() {
        when(transactionService.getById(anyString())).thenReturn(null);
    }

    @Test
    void estadoValidoEsAceptado() throws Exception {
        when(transactionScoringService.updateFraudCaseStatus(eq("tx-1"), eq("EN_REVISION")))
                .thenReturn(RiesgoResponse.pending("tx-1"));

        mockMvc.perform(put("/api/v1/transactions/tx-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EN_REVISION\"}"))
                .andExpect(status().isOk());

        verify(transactionScoringService).updateFraudCaseStatus("tx-1", "EN_REVISION");
    }

    @Test
    void estadoArbitrarioEsRechazado() throws Exception {
        mockMvc.perform(put("/api/v1/transactions/tx-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ESTADO_INVENTADO\"}"))
                .andExpect(status().isBadRequest());

        // No debe llegar a persistir un estado que no esta en la lista blanca.
        verifyNoInteractions(transactionScoringService);
    }

    @Test
    void statusNuloEsRechazado() throws Exception {
        mockMvc.perform(put("/api/v1/transactions/tx-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":null}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionScoringService);
    }

    @Test
    void objetoVacioEsRechazado() throws Exception {
        mockMvc.perform(put("/api/v1/transactions/tx-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionScoringService);
    }
}
