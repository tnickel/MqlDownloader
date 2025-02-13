package utils;



public class StabilityResult {
    private final double value;
    private final String details;

    public StabilityResult(double value, String details) {
        this.value = value;
        this.details = details;
    }

    public double getValue() {
        return value;
    }

    public String getDetails() {
        return details;
    }
}