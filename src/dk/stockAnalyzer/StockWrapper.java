package dk.stockAnalyzer;

import java.io.Serializable;
import java.util.Calendar;
import java.util.HashMap;

/**
 * Created by aogj on 27-09-2015.
 */
public class StockWrapper implements Serializable {

    String name;
    String symbol;
    HashMap<Integer, Double> historicalValues;
    HashMap<Integer, Calendar> historicalValues2;

    public StockWrapper(String name, String symbol, HashMap<Integer, Double> historicalValues, HashMap<Integer, Calendar> historicalValues2) {
        this.name = name;
        this.symbol = symbol;
        this.historicalValues = historicalValues;
        this.historicalValues2 = historicalValues2;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public HashMap<Integer, Double> getHistoricalValues() {
        return historicalValues;
    }

    public void setHistoricalValues(HashMap<Integer, Double> historicalValues) {
        this.historicalValues = historicalValues;
    }

    public HashMap<Integer, Calendar> getHistoricalValues2() {
        return historicalValues2;
    }

    public void setHistoricalValues2(HashMap<Integer, Calendar> historicalValues2) {
        this.historicalValues2 = historicalValues2;
    }
}
