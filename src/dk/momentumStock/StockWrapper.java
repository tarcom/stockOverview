package dk.momentumStock;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

/**
 * Created by aogj on 06-09-2015.
 */
public class StockWrapper {

    Stock stock;

    @Override
    public String toString() {
        return "StockWrapper{" +
                "score=" + roundStr(getScore(), 1) +
                ", name=" + stock.getName() +
                ", symbol=" + stock.getSymbol() +
                ", scoreDebugInfo=" + getScoreDebugInfo() +
                '}';
    }

    public StockWrapper(String stock) {
        this.stock = getStock(stock);
    }


    private Stock getStock(String name) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -10);
        try {
            Stock s = YahooFinance.get(name, cal, Interval.DAILY);
            return s;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public Double getHistDiffProcent(int startDay, int endDay) {
        if (stock == null) return null;

        try {
            Double endDayClose;
            if (endDay == -1) {
                endDayClose = stock.getQuote().getPrice().doubleValue();
            } else {
                endDayClose = stock.getHistory().get(endDay).getClose().doubleValue();
            }


            Double histDiffProcent = ((endDayClose / stock.getHistory().get(startDay).getClose().doubleValue()) - 1) * 100;
            return histDiffProcent;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public String getScoreDebugInfo() {


        String returnStr = "close: ";
        try {

            returnStr += roundStr(stock.getQuote().getPrice().doubleValue(), 2) + ", ";
            returnStr += roundStr(stock.getHistory().get(0).getClose().doubleValue(), 2) + ", ";
            returnStr += roundStr(stock.getHistory().get(1).getClose().doubleValue(), 2) + ", ";
            returnStr += roundStr(stock.getHistory().get(2).getClose().doubleValue(), 2) + ", ";
            returnStr += roundStr(stock.getHistory().get(3).getClose().doubleValue(), 2) + ", ";
            returnStr += roundStr(stock.getHistory().get(4).getClose().doubleValue(), 2) + ", ";;
            returnStr += roundStr(stock.getHistory().get(5).getClose().doubleValue(), 2);

        } catch (Exception e) {
            System.out.println(e);
            return "EXCEPTION. stock=" + stock.getName() + stock.getSymbol();
        }

        returnStr += " --- Pct: ";
        returnStr += roundStr(getHistDiffProcent(0, -1), 2) + "%, ";
        returnStr += roundStr(getHistDiffProcent(1, 0), 2) + "%, ";
        returnStr += roundStr(getHistDiffProcent(2, 1), 2) + "%, ";
        returnStr += roundStr(getHistDiffProcent(3, 2), 2) + "%, ";
        returnStr += roundStr(getHistDiffProcent(4, 3), 2) + "%, ";
        returnStr += roundStr(getHistDiffProcent(5, 4), 2) + "%";

        returnStr += " --- Weight: ";
        Double value = 0d;

        value = getHistDiffProcent(0, -1);
        if (value > 0) value = value * 6;
        else value = value * 18;
        returnStr += roundStr(value, 1) + ", ";


        value = getHistDiffProcent(1, 0);
        if (value > 0) value = value * 5;
        else value = value * 15;
        returnStr += roundStr(value, 1) + ", ";

        value = getHistDiffProcent(2, 1);
        if (value > 0) value = value * 4;
        else value = value * 12;
        returnStr += roundStr(value, 1) + ", ";


        value = getHistDiffProcent(3, 2);
        if (value > 0) value = value * 3;
        else value = value * 9;
        returnStr += roundStr(value, 1) + ", ";


        value = getHistDiffProcent(4, 3);
        if (value > 0) value = value * 2;
        else value = value * 6;
        returnStr += roundStr(value, 1) + ", ";


        value = getHistDiffProcent(5, 4);
        if (value > 0) value = value * 1;
        else value = value * 3;
        returnStr += roundStr(value, 1) + ", ";

        return returnStr;
    }

    /**
     * Currently does not support short stock investment
     * @return allans secrets stock score
     */
    public Double getScore() {

        Double score = 0d;
        Double value = 0d;


        value = getHistDiffProcent(0, -1);
        //System.out.println("value=" + value);
        if (value > 0) value = value * 6;
        else value = value * 18;
        score += value;

        value = getHistDiffProcent(1, 0);
        //System.out.println("value=" + value);
        if (value > 0) value = value * 5;
        else value = value * 15;
        score += value;

        value = getHistDiffProcent(2, 1);
        if (value > 0) value = value * 4;
        else value = value * 12;
        score += value;

        value = getHistDiffProcent(3, 2);
        if (value > 0) value = value * 3;
        else value = value * 9;
        score += value;

        value = getHistDiffProcent(4, 3);
        if (value > 0) value = value * 2;
        else value = value * 6;
        score += value;

        value = getHistDiffProcent(5, 4);
        if (value > 0) value = value * 1;
        else value = value * 3;
        score += value;

        return score;
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

//
//    public Double test(int startDay, int endDay) {
//        for (StockWrapper stock : momentumMap.values()) {
//            Double histDiffProcent = ((stock.getHistory().get(0).getClose().doubleValue() / stock.getHistory().get(daysHist).getClose().doubleValue()) - 1) * 100;
//
//            String hist = "";
//            for (int i = 0; i < daysHist; i++) {
//                Double dayDiffProcent = ((stock.getHistory().get(i).getClose().doubleValue() / stock.getHistory().get(i + 1).getClose().doubleValue()) - 1) * 100;
//                hist += roundStr(dayDiffProcent, 1) + "%, ";
//            }
//
//            System.out.println("(" + roundStr(histDiffProcent, 1) + "%) " + hist + stock.getName() + " (" + stock.getSymbol() + ")");
//        }
//    }
}
