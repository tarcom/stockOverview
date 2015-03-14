package dk.skov;

import java.util.Calendar;
import java.util.TreeMap;

/**
 * Created by aogj on 26-01-14.
 */
public class StockWrapper {

    String symbol;
    TreeMap<Calendar, Double> historicValues;
    Calendar buyDate;

    TreeMap<Calendar, Double> historicValuesIndex100 = new TreeMap<Calendar, Double>();
    TreeMap<Calendar, Double> historicValuesIndex100BeforeBuyDate = new TreeMap<Calendar, Double>();
    TreeMap<Calendar, Double> historicValuesIndex100AfterBuyDate = new TreeMap<Calendar, Double>();
    TreeMap<Calendar, Double> historicValuesBeforeBuyDate = new TreeMap<Calendar, Double>();
    TreeMap<Calendar, Double> historicValuesAfterBuyDate = new TreeMap<Calendar, Double>();


    public StockWrapper(String symbol, TreeMap<Calendar, Double> historicValues, Calendar buyDate) {

        if (!historicValues.containsKey(buyDate)) {
            System.out.println("buyDate for this stock DOES NOT EXIST!! stock symbol = " + symbol);
            System.exit(0);
        }

        this.symbol = symbol;
        this.historicValues = historicValues;
        this.buyDate = buyDate;

        for (Calendar c : historicValues.keySet()) {
            historicValuesIndex100.put(c, historicValues.get(c) / getIndex100Value() * 100);
        }

        for (Calendar c : historicValuesIndex100.keySet()) {
            if (c.before(getBuyDate())) {
                historicValuesIndex100BeforeBuyDate.put(c, historicValuesIndex100.get(c));
            } else {
                historicValuesIndex100AfterBuyDate.put(c, historicValuesIndex100.get(c));
            }
        }

        for (Calendar c : historicValues.keySet()) {
            if (c.before(getBuyDate())) {
                historicValuesBeforeBuyDate.put(c, historicValues.get(c));
            } else {
                historicValuesAfterBuyDate.put(c, historicValues.get(c));
            }
        }

        //System.out.println(Util.printNice(historicValuesIndex100AfterBuyDate));
        //System.out.println(getIndex100Value());
        //System.exit(0);
    }

    public TreeMap<Calendar, Double> getHistoricValuesIndex100() {
        return historicValuesIndex100;
    }

    public Double getIndex100Value() {
        return historicValues.get(buyDate);
    }

    public String getSymbol() {
        return symbol;
    }

    public Calendar getBuyDate() {
        return buyDate;
    }

    public TreeMap<Calendar, Double> getHistoricValues() {
        return historicValues;
    }

    public TreeMap<Calendar, Double> getHistoricValuesIndex100BeforeBuyDate() {
        return historicValuesIndex100BeforeBuyDate;
    }

    public TreeMap<Calendar, Double> getHistoricValuesIndex100AfterBuyDate() {
        return historicValuesIndex100AfterBuyDate;
    }

    public TreeMap<Calendar, Double> getHistoricValuesBeforeBuyDate() {
        return historicValuesBeforeBuyDate;
    }

    public TreeMap<Calendar, Double> getHistoricValuesAfterBuyDate() {
        return historicValuesAfterBuyDate;
    }
}
