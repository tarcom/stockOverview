package dk.stockAnalyzer;

import java.util.Map;

/**
 * Created by aogj on 27-09-2015.
 */
public class GoogleStockCopyPasteLinkGenerator {

    public static void doPrint(Map<Double, StockWrapper> stocks) {

        String googleFinanceCopyPasteString = "";
        for (StockWrapper stock : stocks.values()) {
            googleFinanceCopyPasteString += "CPH:" + stock.getSymbol().replace(".CO", "") + ", ";
        }
        System.out.println("googleFinanceCopyPasteString=" + googleFinanceCopyPasteString);


        //https://www.google.com/finance?q=CPH%3AGEN%2CCPH%3AGJ%2CCPH%3AHOEJ-B&ei=6vTvVdCDBNXcsAGe95GwBA
        googleFinanceCopyPasteString = "https://www.google.com/finance?q=";
        int count = 0;
        for (StockWrapper stock : stocks.values()) {
            if (count++ > stocks.size() - 10) {
                googleFinanceCopyPasteString += "CPH%3A" + stock.getSymbol().replace(".CO", "") + "%2C";
            }
        }
        googleFinanceCopyPasteString = googleFinanceCopyPasteString.substring(0, googleFinanceCopyPasteString.length() - 3);
        googleFinanceCopyPasteString += "&ei=6vTvVdCDBNXcsAGe95GwBA";
        System.out.println("google link top 10:=" + googleFinanceCopyPasteString);




        googleFinanceCopyPasteString = "https://www.google.com/finance?q=";
        count = 0;
        for (StockWrapper stock : stocks.values()) {
            if (count++ > stocks.size() - 6) {
                googleFinanceCopyPasteString += "CPH%3A" + stock.getSymbol().replace(".CO", "") + "%2C";
            }
        }
        googleFinanceCopyPasteString = googleFinanceCopyPasteString.substring(0, googleFinanceCopyPasteString.length() - 3);
        googleFinanceCopyPasteString += "&ei=6vTvVdCDBNXcsAGe95GwBA";
        System.out.println("google link top 6:=" + googleFinanceCopyPasteString);




        //https://www.google.com/finance?q=CPH%3AGEN%2CCPH%3AGJ%2CCPH%3AHOEJ-B&ei=6vTvVdCDBNXcsAGe95GwBA
        googleFinanceCopyPasteString = "https://www.google.com/finance?q=";
        for (Double score : stocks.keySet()) {
            if (score > 20) {
                StockWrapper stock = stocks.get(score);
                googleFinanceCopyPasteString += "CPH%3A" + stock.getSymbol().replace(".CO", "") + "%2C";
            }
        }
        googleFinanceCopyPasteString = googleFinanceCopyPasteString.substring(0, googleFinanceCopyPasteString.length() - 3);
        googleFinanceCopyPasteString += "&ei=6vTvVdCDBNXcsAGe95GwBA";
        System.out.println("google link score over 20:=" + googleFinanceCopyPasteString);


        //https://www.google.com/finance?q=CPH%3AGEN%2CCPH%3AGJ%2CCPH%3AHOEJ-B&ei=6vTvVdCDBNXcsAGe95GwBA
        googleFinanceCopyPasteString = "https://www.google.com/finance?q=";
        for (Double score : stocks.keySet()) {
            if (score > 30) {
                StockWrapper stock = stocks.get(score);
                googleFinanceCopyPasteString += "CPH%3A" + stock.getSymbol().replace(".CO", "") + "%2C";
            }
        }
        googleFinanceCopyPasteString = googleFinanceCopyPasteString.substring(0, googleFinanceCopyPasteString.length() - 3);
        googleFinanceCopyPasteString += "&ei=6vTvVdCDBNXcsAGe95GwBA";
        System.out.println("google link score over 30:=" + googleFinanceCopyPasteString);



        //https://www.google.com/finance?q=CPH%3AGEN%2CCPH%3AGJ%2CCPH%3AHOEJ-B&ei=6vTvVdCDBNXcsAGe95GwBA
        googleFinanceCopyPasteString = "https://www.google.com/finance?q=";
        for (Double score : stocks.keySet()) {
            if (score > 40) {
                StockWrapper stock = stocks.get(score);
                googleFinanceCopyPasteString += "CPH%3A" + stock.getSymbol().replace(".CO", "") + "%2C";
            }
        }
        googleFinanceCopyPasteString = googleFinanceCopyPasteString.substring(0, googleFinanceCopyPasteString.length() - 3);
        googleFinanceCopyPasteString += "&ei=6vTvVdCDBNXcsAGe95GwBA";
        System.out.println("google link score over 40:=" + googleFinanceCopyPasteString);

    }
}
