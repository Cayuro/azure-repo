package com.ingesta.web;

import com.ingesta.config.RateLimitProperties;
import com.ingesta.controller.TransactionController;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sonda de arranque: confirma que la capa web se puede probar de forma aislada,
 * sin levantar el contexto completo y por tanto sin necesitar credenciales de
 * Azure (Cosmos, Storage, Document Intelligence).
 *
 * Todos los tests previos del proyecto usaban @SpringBootTest, que construye
 * CosmosAsyncClient de forma eager y falla sin credenciales. Ese era el motivo
 * de que la suite entera no fuera ejecutable en local ni en CI.
 */
@WebMvcTest(controllers = TransactionController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
class WebLayerBootProbeTest {

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
    void elContextoWebArrancaSinCredencialesDeAzure() {
        assertNotNull(mockMvc);
    }
}
