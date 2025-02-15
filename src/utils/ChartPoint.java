package utils;

public class ChartPoint {
    private final String date;
    private final double value;
    
    public ChartPoint(String date, double value) {
        this.date = date;
        this.value = value;
    }
    
    @Override
    public String toString() {
        return date + "=" + String.format("%.2f", value);
    }

    public String getDate() { 
        return date; 
    }
    
    public double getValue() { 
        return value; 
    }
}