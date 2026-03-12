package io.github.opencivilizationplatform.dto;

public class BalanceDTO {
    private String category;
    private Double supply;
    private Double demand;
    private String unit;
    private Double percentageMet;
    private String status; // STABLE, DEFICIT, CRITICAL

    public BalanceDTO() {}

    public BalanceDTO(String category, Double supply, Double demand, String unit, Double percentageMet, String status) {
        this.category = category;
        this.supply = supply;
        this.demand = demand;
        this.unit = unit;
        this.percentageMet = percentageMet;
        this.status = status;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getSupply() { return supply; }
    public void setSupply(Double supply) { this.supply = supply; }
    public Double getDemand() { return demand; }
    public void setDemand(Double demand) { this.demand = demand; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getPercentageMet() { return percentageMet; }
    public void setPercentageMet(Double percentageMet) { this.percentageMet = percentageMet; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
