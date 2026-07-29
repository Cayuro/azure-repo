package com.ingesta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ingesta.scoring")
public class ScoringProperties {

    private int threshold = 60;
    private int velocityWindowMinutes = 3;
    private int velocityMinimumTransactions = 3;
    private int velocityPoints = 35;
    private double amountMultiplier = 5.0;
    private int amountPoints = 30;
    private double geoMaxSpeedKmH = 1000.0;
    private int geoPoints = 17;
    private int merchantPoints = 20;
    private List<String> riskMerchantCategories = new ArrayList<>();

    /**
     * Obtiene el puntaje umbral para ser considerado fraude.
     * @return Puntaje umbral.
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * Establece el puntaje umbral para ser considerado fraude.
     * @param threshold Puntaje umbral.
     */
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /**
     * Obtiene el intervalo de tiempo en minutos para el cálculo de velocidad.
     * @return Intervalo de tiempo en minutos.
     */
    public int getVelocityWindowMinutes() {
        return velocityWindowMinutes;
    }

    /**
     * Establece el intervalo de tiempo en minutos para el cálculo de velocidad.
     * @param velocityWindowMinutes Intervalo de tiempo en minutos.
     */
    public void setVelocityWindowMinutes(int velocityWindowMinutes) {
        this.velocityWindowMinutes = velocityWindowMinutes;
    }

    /**
     * Obtiene el número mínimo de transacciones para el cálculo de velocidad.
     * @return Número mínimo de transacciones.
     */
    public int getVelocityMinimumTransactions() {
        return velocityMinimumTransactions;
    }

    /**
     * Establece el número mínimo de transacciones para el cálculo de velocidad.
     * @param velocityMinimumTransactions Número mínimo de transacciones.
     */
    public void setVelocityMinimumTransactions(int velocityMinimumTransactions) {
        this.velocityMinimumTransactions = velocityMinimumTransactions;
    }

    /**
     * Obtiene el puntaje asignado por velocidad de transacciones.
     * @return Puntaje por velocidad.
     */
    public int getVelocityPoints() {
        return velocityPoints;
    }

    /**
     * Establece el puntaje asignado por velocidad de transacciones.
     * @param velocityPoints Puntaje por velocidad.
     */
    public void setVelocityPoints(int velocityPoints) {
        this.velocityPoints = velocityPoints;
    }

    /**
     * Obtiene el multiplicador aplicado al monto de la transacción para el cálculo de puntos.
     * @return Multiplicador de monto.
     */
    public double getAmountMultiplier() {
        return amountMultiplier;
    }

    /**
     * Establece el multiplicador aplicado al monto de la transacción para el cálculo de puntos.
     * @param amountMultiplier Multiplicador de monto.
     */
    public void setAmountMultiplier(double amountMultiplier) {
        this.amountMultiplier = amountMultiplier;
    }

    /**
     * Obtiene el puntaje asignado por monto de transacción.
     * @return Puntaje por monto.
     */
    public int getAmountPoints() {
        return amountPoints;
    }

    /**
     * Establece el puntaje asignado por monto de transacción.
     * @param amountPoints Puntaje por monto.
     */
    public void setAmountPoints(int amountPoints) {
        this.amountPoints = amountPoints;
    }

    public double getGeoMaxSpeedKmH() {
        return geoMaxSpeedKmH;
    }

    public void setGeoMaxSpeedKmH(double geoMaxSpeedKmH) {
        this.geoMaxSpeedKmH = geoMaxSpeedKmH;
    }

    public int getGeoPoints() {
        return geoPoints;
    }

    public void setGeoPoints(int geoPoints) {
        this.geoPoints = geoPoints;
    }

    public int getMerchantPoints() {
        return merchantPoints;
    }

    public void setMerchantPoints(int merchantPoints) {
        this.merchantPoints = merchantPoints;
    }

    public List<String> getRiskMerchantCategories() {
        return riskMerchantCategories;
    }

    public void setRiskMerchantCategories(List<String> riskMerchantCategories) {
        this.riskMerchantCategories = riskMerchantCategories;
    }
}