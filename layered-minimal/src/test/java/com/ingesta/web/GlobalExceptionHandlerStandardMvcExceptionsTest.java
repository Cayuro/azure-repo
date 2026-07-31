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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG 1: GlobalExceptionHandler tiene un catch-all de Exception.class y la clase NO
 * extiende ResponseEntityExceptionHandler, asi que excepciones estandar de Spring MVC
 * que deberian mapear a su propio codigo 4xx caen en el catch-all y se devuelven como 500.
 *
 * Estos tests miden EMPIRICAMENTE el comportamiento actual (antes de la correccion) para
 * cada una de las excepciones senaladas en el reporte de bugs. Los valores esperados aqui
 * son los CORRECTOS (los que deberia devolver Spring); antes de aplicar el fix, el reporte
 * de fallos de mvn test contiene el valor "actual" que hoy produce el sistema (500 en todos
 * los casos salvo que se indique lo contrario).
 */
@WebMvcTest(controllers = TransactionController.class)
@EnableConfigurationProperties(RateLimitProperties.class)
class GlobalExceptionHandlerStandardMvcExceptionsTest {

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
    void metodoHttpNoSoportadoDevuelve405() throws Exception {
        // /api/v1/transactions solo mapea GET y POST; DELETE debe disparar
        // HttpRequestMethodNotSupportedException, que Spring mapea a 405.
        mockMvc.perform(delete("/api/v1/transactions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void tipoDeMediaNoSoportadoDevuelve415() throws Exception {
        // Content-Type text/plain en un endpoint que solo consume JSON debe disparar
        // HttpMediaTypeNotSupportedException, que Spring mapea a 415.
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("no soy json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void tipoDeMediaNoAceptableDevuelve406() throws Exception {
        // Accept: application/xml no puede satisfacerse (no hay conversor XML registrado)
        // para el listado de transacciones; dispara HttpMediaTypeNotAcceptableException.
        //
        // HALLAZGO EMPIRICO: a diferencia de los otros 4 casos de este archivo, este YA
        // devolvia 406 (no 500) incluso antes de la correccion del BUG 1: la excepcion se
        // levanta durante la escritura de la respuesta (negociacion de contenido), y
        // DefaultHandlerExceptionResolver la resuelve directamente sin pasar por el
        // catch-all Exception.class. El cuerpo queda vacio en ambos casos (antes y despues
        // del fix) porque, al pedir Accept: application/xml sin tener un conversor XML
        // registrado, el servidor tampoco puede escribir NUESTRO ApiErrorResponse en JSON
        // sin violar ese mismo Accept header. No es un efecto del catch-all: es una
        // consecuencia inevitable de negociacion de contenido estricta.
        mockMvc.perform(get("/api/v1/transactions").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void parteMultipartFaltanteDevuelve400() throws Exception {
        // El endpoint de subida de evidencia requiere la parte "file"; si no se envia,
        // Spring dispara MissingServletRequestPartException, que mapea a 400.
        mockMvc.perform(multipart("/api/v1/transactions/tx-1/evidencias"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rutaInexistenteDevuelve404() throws Exception {
        // Una ruta sin handler registrado debe disparar NoResourceFoundException (404),
        // no el catch-all de 500.
        mockMvc.perform(get("/api/v1/esta-ruta-no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
