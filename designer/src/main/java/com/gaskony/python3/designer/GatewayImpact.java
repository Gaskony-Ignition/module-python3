package com.gaskony.python3.designer;

/**
 * Represents Gateway performance impact assessment from Python 3 module.
 * Contains impact level classification and overall health score.
 */
public class GatewayImpact {
    private String impactLevel;          // LOW, MODERATE, HIGH, CRITICAL
    private int healthScore;             // 0-100
    private String recommendation;
    private Double memoryUsageMb;        // v2.5.19: RAM usage in MB (legacy - now gatewayMemoryMb)
    private Double averageCpuTimeMs;     // v2.5.19: Average CPU time in milliseconds
    private Double cpuUsagePercent;      // v2.5.21: CPU usage as percentage (legacy - now gatewayCpuPercent)

    // v2.15.5: Python3-specific and system-wide metrics
    private Double python3MemoryMb;      // Python3 subprocess memory usage
    private Double python3CpuPercent;    // Python3 subprocess CPU usage %
    private Double gatewayMemoryMb;      // Gateway JVM memory usage
    private Double gatewayCpuPercent;    // Gateway historical CPU average %
    private Double maxMemoryMb;          // JVM max memory configured
    private Integer availableCores;      // Number of CPU cores

    public GatewayImpact() {
    }

    public GatewayImpact(String impactLevel, int healthScore, String recommendation) {
        this.impactLevel = impactLevel;
        this.healthScore = healthScore;
        this.recommendation = recommendation;
    }

    public String getImpactLevel() {
        return impactLevel;
    }

    public void setImpactLevel(String impactLevel) {
        this.impactLevel = impactLevel;
    }

    public int getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(int healthScore) {
        this.healthScore = healthScore;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    // v2.5.19: Getters and setters for memory and CPU usage
    public Double getMemoryUsageMb() {
        return memoryUsageMb;
    }

    public void setMemoryUsageMb(Double memoryUsageMb) {
        this.memoryUsageMb = memoryUsageMb;
    }

    public Double getAverageCpuTimeMs() {
        return averageCpuTimeMs;
    }

    public void setAverageCpuTimeMs(Double averageCpuTimeMs) {
        this.averageCpuTimeMs = averageCpuTimeMs;
    }

    // v2.5.21: Getters and setters for CPU usage percentage
    public Double getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    public void setCpuUsagePercent(Double cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }

    // v2.15.5: Getters and setters for Python3-specific and system-wide metrics
    public Double getPython3MemoryMb() {
        return python3MemoryMb;
    }

    public void setPython3MemoryMb(Double python3MemoryMb) {
        this.python3MemoryMb = python3MemoryMb;
    }

    public Double getPython3CpuPercent() {
        return python3CpuPercent;
    }

    public void setPython3CpuPercent(Double python3CpuPercent) {
        this.python3CpuPercent = python3CpuPercent;
    }

    public Double getGatewayMemoryMb() {
        return gatewayMemoryMb;
    }

    public void setGatewayMemoryMb(Double gatewayMemoryMb) {
        this.gatewayMemoryMb = gatewayMemoryMb;
    }

    public Double getGatewayCpuPercent() {
        return gatewayCpuPercent;
    }

    public void setGatewayCpuPercent(Double gatewayCpuPercent) {
        this.gatewayCpuPercent = gatewayCpuPercent;
    }

    public Double getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public void setMaxMemoryMb(Double maxMemoryMb) {
        this.maxMemoryMb = maxMemoryMb;
    }

    public Integer getAvailableCores() {
        return availableCores;
    }

    public void setAvailableCores(Integer availableCores) {
        this.availableCores = availableCores;
    }

    @Override
    public String toString() {
        return "GatewayImpact{" +
                "impactLevel='" + impactLevel + '\'' +
                ", healthScore=" + healthScore +
                ", recommendation='" + recommendation + '\'' +
                ", memoryUsageMb=" + memoryUsageMb +
                ", averageCpuTimeMs=" + averageCpuTimeMs +
                ", cpuUsagePercent=" + cpuUsagePercent +
                '}';
    }
}
