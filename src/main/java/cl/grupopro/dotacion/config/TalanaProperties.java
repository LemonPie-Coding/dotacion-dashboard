package cl.grupopro.dotacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de acceso a la API de Talana. Propia de este proyecto
 * (no depende del repo cerrado).
 */
@ConfigurationProperties(prefix = "talana")
public class TalanaProperties {

    private String baseUrl;
    private String token;
    private long delayEntrePeticionesMs = 2000;
    private int maxPaginas = 50;
    private int cacheMinutos = 10;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public long getDelayEntrePeticionesMs() { return delayEntrePeticionesMs; }
    public void setDelayEntrePeticionesMs(long v) { this.delayEntrePeticionesMs = v; }

    public int getMaxPaginas() { return maxPaginas; }
    public void setMaxPaginas(int v) { this.maxPaginas = v; }

    public int getCacheMinutos() { return cacheMinutos; }
    public void setCacheMinutos(int v) { this.cacheMinutos = v; }
}
