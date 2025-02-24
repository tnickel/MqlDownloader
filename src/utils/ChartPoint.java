package utils;

public class ChartPoint {
    private String date;
    private double value; // The drawdown percentage

    public ChartPoint(String date, double value) {
        this.date = date;
        this.value = value;
    }

    public String getDate() {
        return date;
    }

    public double getValue() {
        return value;
    }
}

