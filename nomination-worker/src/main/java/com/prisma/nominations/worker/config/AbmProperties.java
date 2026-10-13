package com.prisma.nominations.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros de integracion con ABM y politica de resiliencia.
 */
@ConfigurationProperties(prefix = "app.abm")
public class AbmProperties {

    /** URL base del sistema ABM. */
    private String baseUrl = "http://localhost:8082";

    /** Timeout de la llamada de envio. */
    private Duration timeout = Duration.ofSeconds(3);

    /** Maximo de reintentos (incluye el primer envio). */
    private int maxAttempts = 4;

    /** Ventana de espera de la respuesta asincronica de ABM antes de considerarla timeout. */
    private Duration waitWindow = Duration.ofSeconds(30);

    /** Cantidad de nominaciones procesadas por ciclo. */
    private int batchSize = 50;

    /** Backoff base para reintentos (exponencial con jitter). */
    private Duration backoffBase = Duration.ofSeconds(2);

    /** Retraso simulado del callback en el ABM mock. */
    private Duration mockCallbackDelay = Duration.ofMillis(300);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getWaitWindow() {
        return waitWindow;
    }

    public void setWaitWindow(Duration waitWindow) {
        this.waitWindow = waitWindow;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(Duration backoffBase) {
        this.backoffBase = backoffBase;
    }

    public Duration getMockCallbackDelay() {
        return mockCallbackDelay;
    }

    public void setMockCallbackDelay(Duration mockCallbackDelay) {
        this.mockCallbackDelay = mockCallbackDelay;
    }
}
