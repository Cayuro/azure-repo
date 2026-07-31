package com.ingesta.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VULN 2 (ALTO, evasion del limite) y VULN 3 (ALTO, fuga de memoria) de RateLimitingFilter.
 *
 * Se construye el filtro directamente con un RateLimitProperties armado a mano (sin
 * levantar contexto de Spring) y se monta con MockMvcBuilders.standaloneSetup(...)
 * .addFilters(filtro) sobre un controlador de prueba minimo, para probar la logica de
 * seguridad del filtro de forma aislada.
 *
 * MockMvc por defecto informa el mismo remoteAddr ("127.0.0.1") en cada peticion, lo cual
 * es justo lo que se necesita: permite comprobar que, sin ese remoteAddr en la lista
 * blanca de proxies de confianza, todas las peticiones cuentan como el MISMO origen sin
 * importar que valor traiga X-Forwarded-For.
 */
class RateLimitingFilterSecurityTest {

    @RestController
    static class ControladorDePrueba {
        @GetMapping("/api/ping")
        public String ping() {
            return "pong";
        }
    }

    private RateLimitProperties propiedades(List<String> proxiesDeConfianza) {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setWindowSeconds(60);
        props.setMaxRequests(2);
        props.setPathPrefix("/api/");
        props.setTrustedProxies(proxiesDeConfianza);
        return props;
    }

    private MockMvc mockMvcCon(RateLimitProperties props, RateLimitingFilter filtro) {
        return MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .addFilters(filtro)
                .build();
    }

    @Test
    void rotarXForwardedForYaNoEvadeElLimiteCuandoElClienteNoEsUnProxyDeConfianza() throws Exception {
        // Antes del fix: cada peticion con un X-Forwarded-For distinto era tratada como un
        // origen nuevo -> cuota infinita. Ahora, como 127.0.0.1 (el remoteAddr real que
        // usa MockMvc) NO esta en la lista blanca, el filtro debe IGNORAR la cabecera.
        RateLimitingFilter filtro = new RateLimitingFilter(propiedades(List.of()), new ObjectMapper());
        MockMvc mockMvc = mockMvcCon(propiedades(List.of()), filtro);

        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "1.1.1.1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "2.2.2.2"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "3.3.3.3"))
                .andExpect(status().isTooManyRequests());

        // Un unico origen en memoria (127.0.0.1), pese a haber usado tres valores de
        // cabecera distintos: confirma que no se crearon entradas nuevas por origen falso.
        assertEquals(1, filtro.origenesEnMemoria());
    }

    @Test
    void xForwardedForSiSeRespetaCuandoLaPeticionVieneDeUnProxyDeConfianza() throws Exception {
        // Con 127.0.0.1 (el remoteAddr real) en la lista blanca, dos "clientes reales"
        // detras del proxy (distinto X-Forwarded-For) deben contar por separado.
        RateLimitProperties props = propiedades(List.of("127.0.0.1"));
        RateLimitingFilter filtro = new RateLimitingFilter(props, new ObjectMapper());
        MockMvc mockMvc = mockMvcCon(props, filtro);

        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "9.9.9.9")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "9.9.9.9")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "9.9.9.9")).andExpect(status().isTooManyRequests());

        // Un segundo origen real detras del mismo proxy no deberia estar limitado todavia.
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "8.8.8.8")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ping").header("X-Forwarded-For", "8.8.8.8")).andExpect(status().isOk());

        assertEquals(2, filtro.origenesEnMemoria());
    }

    @Test
    void elBarridoPeriodicoLiberaLosOrigenesCuyaVentanaYaCaduco() throws Exception {
        // VULN 3: sin purga, requestsByOrigin crece indefinidamente. Se usa una ventana de
        // 1 segundo para no alargar el test.
        RateLimitProperties props = propiedades(List.of());
        props.setWindowSeconds(1);
        RateLimitingFilter filtro = new RateLimitingFilter(props, new ObjectMapper());
        MockMvc mockMvc = mockMvcCon(props, filtro);

        mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        assertEquals(1, filtro.origenesEnMemoria());

        Thread.sleep(1100);
        filtro.purgeExpiredOrigins();

        assertEquals(0, filtro.origenesEnMemoria());
    }
}
