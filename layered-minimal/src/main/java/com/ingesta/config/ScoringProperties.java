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

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getVelocityWindowMinutes() {
        return velocityWindowMinutes;
    }

    public void setVelocityWindowMinutes(int velocityWindowMinutes) {
        this.velocityWindowMinutes = velocityWindowMinutes;
    }

    public int getVelocityMinimumTransactions() {
        return velocityMinimumTransactions;
    }

    public void setVelocityMinimumTransactions(int velocityMinimumTransactions) {
        this.velocityMinimumTransactions = velocityMinimumTransactions;
    }

    public int getVelocityPoints() {
        return velocityPoints;
    }

    public void setVelocityPoints(int velocityPoints) {
        this.velocityPoints = velocityPoints;
    }

    public double getAmountMultiplier() {
        return amountMultiplier;
    }

    public void setAmountMultiplier(double amountMultiplier) {
        this.amountMultiplier = amountMultiplier;
    }

    public int getAmountPoints() {
        return amountPoints;
    }

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