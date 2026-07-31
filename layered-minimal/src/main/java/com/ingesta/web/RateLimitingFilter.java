package com.ingesta.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingesta.config.RateLimitProperties;
import com.ingesta.dto.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Deque<Long>> requestsByOrigin = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith(properties.getPathPrefix());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String origin = resolveOrigin(request);
        long now = System.currentTimeMillis();
        long windowMillis = properties.getWindowSeconds() * 1000L;

        Deque<Long> requests = requestsByOrigin.computeIfAbsent(origin, ignored -> new ArrayDeque<>());
        int remaining;
        long resetSeconds;
        synchronized (requests) {
            prune(requests, now - windowMillis);
            if (requests.size() >= properties.getMaxRequests()) {
                long oldest = requests.peekFirst() == null ? now : requests.peekFirst();
                resetSeconds = Math.max(1L, (windowMillis - (now - oldest) + 999L) / 1000L);
                writeHeaders(response, 0, resetSeconds);
                response.setHeader("Retry-After", String.valueOf(resetSeconds));
                response.setStatus(429);
                response.setContentType("application/json");
                objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(
                    429,
                        "Too Many Requests",
                        "Se excedio el limite de peticiones permitido",
                        java.util.List.of("Espere " + resetSeconds + " segundos antes de volver a intentar")));
                return;
            }
            requests.addLast(now);
            prune(requests, now - windowMillis);
            remaining = Math.max(0, properties.getMaxRequests() - requests.size());
            long oldest = requests.peekFirst() == null ? now : requests.peekFirst();
            resetSeconds = Math.max(1L, (windowMillis - (now - oldest) + 999L) / 1000L);
        }

        writeHeaders(response, remaining, resetSeconds);
        filterChain.doFilter(request, response);
    }

    private void writeHeaders(HttpServletResponse response, int remaining, long resetSeconds) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.getMaxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(Instant.now().getEpochSecond() + resetSeconds));
    }

    private void prune(Deque<Long> requests, long oldestAllowed) {
        while (!requests.isEmpty() && requests.peekFirst() < oldestAllowed) {
            requests.removeFirst();
        }
    }

    /**
     * VULN 2 (ALTO, evasion del limite de peticiones): antes se confiaba ciegamente en
     * X-Forwarded-For, una cabecera que pone el propio cliente HTTP. Un cliente directo
     * (sin ningun proxy real en medio) podia enviar un valor aleatorio distinto en cada
     * peticion y el filtro lo trataba como si fuera un origen nuevo cada vez, obteniendo
     * cuota infinita.
     *
     * FIX: solo se confia en X-Forwarded-For cuando request.getRemoteAddr() -- la
     * direccion TCP real de quien conecto, que un cliente NO puede falsificar -- esta en
     * la lista blanca configurable ingesta.ratelimit.trusted-proxies. Si la peticion no
     * viene de un proxy conocido (incluida la config por defecto, sin proxies listados),
     * se usa siempre getRemoteAddr() y la cabecera se ignora por completo.
     *
     * Alternativas consideradas y descartadas:
     * - server.forward-headers-strategy=framework / NATIVE: reescribe getRemoteAddr()
     *   (y el scheme/host usados en redirects) para TODA la aplicacion basandose en
     *   X-Forwarded-*, no solo para este filtro. Sigue sin resolver el problema de raiz
     *   -- Spring documenta explicitamente que solo debe activarse si un proxy de
     *   confianza filtra/sobreescribe esas cabeceras antes de que lleguen a la app --
     *   y ademas amplia el radio de impacto (logs de auditoria, matchers de IP en
     *   Spring Security, etc.) a algo que aqui solo necesita resolverse para el rate
     *   limiter.
     * - ForwardedHeaderFilter: mismo problema (confia en la cabecera salvo que algo
     *   aguas arriba ya la filtre) y ademas se ejecuta como filtro de Spring MVC, con lo
     *   cual quedaria DESPUES de este filtro (@Order(HIGHEST_PRECEDENCE)) en la cadena;
     *   habria que reordenar todo el filter chain para depender de el.
     * Se prefiere una lista blanca explicita y local a este filtro: mas simple de
     * auditar, no cambia el comportamiento del resto de la app, y dice exactamente en
     * que confia (la IP del proxy, no la cabecera).
     */
    private String resolveOrigin(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!properties.getTrustedProxies().contains(remoteAddr)) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    /**
     * VULN 3 (ALTO, fuga de memoria / DoS): requestsByOrigin nunca purgaba los origenes
     * inactivos. Combinado con VULN 2 (antes de su fix, la clave del mapa era una cabecera
     * arbitraria puesta por el atacante), cada peticion con un X-Forwarded-For distinto
     * creaba una entrada nueva que jamas se liberaba, agotando la memoria del proceso.
     *
     * FIX: barrido periodico (sin dependencias nuevas: @Scheduled ya viene de
     * spring-context, y @EnableScheduling ya esta activo en IngestaApplication) que, para
     * cada origen, poda su Deque de timestamps fuera de la ventana vigente y elimina del
     * mapa los que quedan vacios. El intervalo es configurable
     * (ingesta.ratelimit.cleanup-interval-ms, 60s por defecto) para poder ajustarlo sin
     * recompilar. remove(key, value) es la variante atomica de ConcurrentHashMap: solo
     * borra la entrada si nadie la reemplazo mientras se recorria (p.ej. una peticion
     * concurrente que acaba de crear el Deque via computeIfAbsent).
     */
    // Placeholder de propiedad directo en vez de una referencia SpEL a un bean
    // ("#{@rateLimitProperties...}"): con @ConfigurationPropertiesScan el nombre de bean
    // generado para una clase @ConfigurationProperties con prefijo NO es el nombre simple
    // decapitalizado de la clase (rompia con NoSuchBeanDefinitionException en cualquier
    // contexto que activa @Scheduled, incluidos los @WebMvcTest que heredan
    // @EnableScheduling de IngestaApplication). Referenciar la propiedad directamente
    // evita depender de ese detalle de implementacion.
    @Scheduled(fixedDelayString = "${ingesta.ratelimit.cleanup-interval-ms:60000}")
    void purgeExpiredOrigins() {
        long oldestAllowed = System.currentTimeMillis() - properties.getWindowSeconds() * 1000L;
        requestsByOrigin.forEach((origin, requests) -> {
            synchronized (requests) {
                prune(requests, oldestAllowed);
                if (requests.isEmpty()) {
                    requestsByOrigin.remove(origin, requests);
                }
            }
        });
    }

    /** Visibilidad de paquete para que el test verifique el barrido sin usar reflection. */
    int origenesEnMemoria() {
        return requestsByOrigin.size();
    }
}