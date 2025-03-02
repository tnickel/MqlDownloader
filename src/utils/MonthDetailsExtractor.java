package utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MonthDetailsExtractor {
    private static final Logger LOGGER = Logger.getLogger(MonthDetailsExtractor.class.getName());
    private final HtmlContentCache contentCache;
    
    public MonthDetailsExtractor(HtmlContentCache contentCache) {
        this.contentCache = contentCache;
    }
    
    public List<String> getLastThreeMonthsDetails(String fileName) {
        List<String> details = new ArrayList<>();
        List<String> months = new ArrayList<>();
        Set<String> seenDates = new HashSet<>();
        try {
            String htmlContent = contentCache.getHtmlContent(fileName);
            if (htmlContent == null) return details;
            Pattern yearRowPattern = Pattern.compile(
                "<tr>\\s*<td[^>]*>(\\d{4})</td>\\s*((?:<td[^>]*>([^<]*)</td>\\s*){12})"
            );
            Matcher rowMatcher = yearRowPattern.matcher(htmlContent);
            boolean foundDuplicate = false;
            while (rowMatcher.find() && !foundDuplicate) {
                String year = rowMatcher.group(1);
                String monthsContent = rowMatcher.group(2);
                Pattern valuePattern = Pattern.compile("<td[^>]*>([^<]*)</td>");
                Matcher valueMatcher = valuePattern.matcher(monthsContent);
                int monthIndex = 0;
                while (valueMatcher.find() && monthIndex < 12) {
                    String value = valueMatcher.group(1).trim();
                    if (!value.isEmpty()) {
                        value = value.replace(",", ".")
                                     .replace("−", "-")
                                     .replaceAll("[^0-9.\\-]", "");
                        if (!value.isEmpty()) {
                            String date = year + "/" + String.format("%02d", monthIndex + 1);
                            if (seenDates.contains(date)) {
                                foundDuplicate = true;
                                break;
                            }
                            seenDates.add(date);
                            months.add(date + ":" + value);
                        }
                    }
                    monthIndex++;
                }
            }
            if (months.size() >= 2) {
                int startIndex = months.size() - 2;
                int monthsToUse = Math.min(3, startIndex + 1);
                for (int i = startIndex; i > startIndex - monthsToUse; i--) {
                    details.add(months.get(i));
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing HTML for " + fileName + ": " + e.getMessage());
        }
        return details;
    }
    
    public List<String> getAllMonthsDetails(String fileName) {
        List<String> allMonths = new ArrayList<>();
        Set<String> seenDates = new HashSet<>();
        try {
            String htmlContent = contentCache.getHtmlContent(fileName);
            if (htmlContent == null) return allMonths;
            Pattern yearRowPattern = Pattern.compile(
                "<tr>\\s*<td[^>]*>(\\d{4})</td>\\s*((?:<td[^>]*>([^<]*)</td>\\s*){12})"
            );
            Matcher rowMatcher = yearRowPattern.matcher(htmlContent);
            boolean foundDuplicate = false;
            while (rowMatcher.find() && !foundDuplicate) {
                String year = rowMatcher.group(1);
                String monthsContent = rowMatcher.group(2);
                Pattern valuePattern = Pattern.compile("<td[^>]*>([^<]*)</td>");
                Matcher valueMatcher = valuePattern.matcher(monthsContent);
                int monthIndex = 0;
                while (valueMatcher.find() && monthIndex < 12) {
                    String value = valueMatcher.group(1).trim();
                    if (!value.isEmpty()) {
                        value = value.replace(",", ".")
                                   .replace("−", "-")
                                   .replaceAll("[^0-9.\\-]", "");
                        if (!value.isEmpty()) {
                            String date = year + "/" + String.format("%02d", monthIndex + 1);
                            if (seenDates.contains(date)) {
                                foundDuplicate = true;
                                break;
                            }
                            seenDates.add(date);
                            allMonths.add(date + ":" + value);
                        }
                    }
                    monthIndex++;
                }
            }
            Collections.sort(allMonths);
        } catch (Exception e) {
            LOGGER.severe("Error processing HTML for " + fileName + ": " + e.getMessage());
        }
        return allMonths;
    }
}