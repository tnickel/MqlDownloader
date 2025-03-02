package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HtmlParser {
    private static final Logger Logger =  LogManager.getLogger(HtmlContentCache.class);
   
    
    private final String rootPath;
    private final HtmlContentCache contentCache;
    private final ChartDataExtractor chartExtractor;
    private final MonthDetailsExtractor monthExtractor;
    private final StabilityCalculator stabilityCalculator;
    private final DataExtractor dataExtractor;
    
    public HtmlParser(String rootPath) {
        this.rootPath = rootPath;
        this.contentCache = new HtmlContentCache(rootPath);
        this.chartExtractor = new ChartDataExtractor(contentCache);
        this.monthExtractor = new MonthDetailsExtractor(contentCache);
        this.stabilityCalculator = new StabilityCalculator(monthExtractor, contentCache);
        this.dataExtractor = new DataExtractor(contentCache, chartExtractor);
    }
    
    public String getHtmlContent(String fileName) {
        return contentCache.getHtmlContent(fileName);
    }
    
    public double getBalance(String fileName) {
        return dataExtractor.getBalance(fileName);
    }
    
    public double getEquityDrawdownGraphic(String fileName) {
        return dataExtractor.getEquityDrawdownGraphic(fileName);
    }
    
    public double getEquityDrawdown(String fileName) {
        return dataExtractor.getEquityDrawdown(fileName);
    }
    
    public double getAvr3MonthProfit(String fileName) {
        return dataExtractor.getAvr3MonthProfit(fileName, monthExtractor);
    }
    
    public List<ChartPoint> getDrawdownChartData(String fileName) {
        return chartExtractor.getDrawdownChartData(fileName);
    }
    
    public List<String> getLastThreeMonthsDetails(String fileName) {
        return monthExtractor.getLastThreeMonthsDetails(fileName);
    }
    
    public List<String> getAllMonthsDetails(String fileName) {
        return monthExtractor.getAllMonthsDetails(fileName);
    }
    
    public double getStabilitaetswert(String fileName) {
        return stabilityCalculator.getStabilitaetswert(fileName);
    }
    
    public StabilityResult getStabilitaetswertDetails(String fileName) {
        return stabilityCalculator.getStabilitaetswertDetails(fileName);
    }
    
    public void writeEquityDrawdownToFile(String fileName, String outputFilePath) {
        dataExtractor.writeEquityDrawdownToFile(fileName, outputFilePath);
    }
}