package dk.momentumStock;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by aogj on 04-09-2015.
 */
public class Test1 {

    public static void main(String... args) throws Exception {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -10);

        String[] symbols = new String[]{"DFDS.CO", "GEN.CO", "PAAL-B.CO"};
        Map<String, Stock> stocks = YahooFinance.get(symbols, cal, Interval.DAILY);

        TreeMap<Double, String> momentumMap = new TreeMap<Double, String>();
        for (String stockName : stocks.keySet()) {
            ArrayList<Object> stockInfo = megaTest(stocks.get(stockName), 4);
            momentumMap.put((Double) stockInfo.get(0), (String) stockInfo.get(1));
        }


    }

    private static ArrayList<Object> megaTest(Stock stock, int daysHist) throws IOException {

        String hist = "";

        for (int i = 0; i < daysHist; i++) {
            Double dayDiffProcent = ((stock.getHistory().get(i).getClose().doubleValue() / stock.getHistory().get(i + 1).getClose().doubleValue()) - 1) * 100;
            hist += roundStr(dayDiffProcent, 1) + "%, ";
        }

        hist += stock.getName();

        Double fiveDayDiffProcent = ((stock.getHistory().get(0).getClose().doubleValue() / stock.getHistory().get(daysHist).getClose().doubleValue()) - 1) * 100;
        System.out.println("(" + roundStr(fiveDayDiffProcent, 1) + "%), " + hist);

        ArrayList<Object> returnList = new ArrayList<Object>();
        returnList.add(fiveDayDiffProcent);
        returnList.add(hist);
        return returnList;
    }

    public static String roundStr(double value, int places) {
        value = round(value, places);


        if (value < 0) {
            return String.valueOf(value);
        } else {
            return " " + String.valueOf(value);
        }
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    private static void test4() throws IOException {
        String[] symbols = new String[]{"INTC", "BABA", "TSLA", "AIR.PA", "YHOO"};
        // Can also be done with explicit from, to and Interval parameters
        Map<String, Stock> stocks = YahooFinance.get(symbols, true);
        Stock intel = stocks.get("INTC");
        Stock airbus = stocks.get("AIR.PA");
    }

    private static void test3() throws IOException {
        Stock tesla = YahooFinance.get("TSLA", true);
        System.out.println(tesla.getHistory());
    }

    private static void test2() throws IOException {
        String[] symbols = new String[]{"INTC", "BABA", "TSLA", "AIR.PA", "YHOO"};
        Map<String, Stock> stocks = YahooFinance.get(symbols); // single request
        Stock intel = stocks.get("INTC");
        Stock airbus = stocks.get("AIR.PA");
    }

    private static void test1() throws IOException {
        Stock stock = YahooFinance.get("INTC");

        BigDecimal price = stock.getQuote().getPrice();
        BigDecimal change = stock.getQuote().getChangeInPercent();
        BigDecimal peg = stock.getStats().getPeg();
        BigDecimal dividend = stock.getDividend().getAnnualYieldPercent();

        stock.print();
    }
}
