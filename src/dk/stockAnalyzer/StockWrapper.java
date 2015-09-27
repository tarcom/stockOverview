package dk.stockAnalyzer;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by aogj on 27-09-2015.
 */
public class StockWrapper implements Serializable {

    String name;
    String symbol;
    HashMap<Integer, Double> historicalValues;

    public StockWrapper(String name, String symbol, HashMap<Integer, Double> historicalValues) {
        this.name = name;
        this.symbol = symbol;
        this.historicalValues = historicalValues;
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
}
