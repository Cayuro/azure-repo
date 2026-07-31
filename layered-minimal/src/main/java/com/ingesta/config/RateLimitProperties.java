package com.ingesta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ingesta.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int windowSeconds = 60;
    private int maxRequests = 30;
    private String pathPrefix = "/api/";

    // VULN 2: lista blanca de direcciones IP de proxies de confianza (balanceador/ingress
    // delante de la app). Vacia por defecto -- a proposito: mientras no se configure
    // explicitamente, la cabecera X-Forwarded-For NO se confia y se usa siempre
    // getRemoteAddr(), que es lo unico que un cliente directo no puede falsificar.
    // Configurar via ingesta.ratelimit.trusted-proxies=10.0.0.4,10.0.0.5 (IP del proxy
    // real, NO del cliente) solo cuando la app este detras de un proxy/ingress conocido.
    private List<String> trustedProxies = List.of();

    // VULN 3: intervalo del barrido periodico que libera del mapa requestsByOrigin los
    // origenes cuya ventana de peticiones ya caduco. Ver RateLimitingFilter#purgeExpiredOrigins.
    private long cleanupIntervalMs = 60_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        this.cleanupIntervalMs = cleanupIntervalMs;
    }
}