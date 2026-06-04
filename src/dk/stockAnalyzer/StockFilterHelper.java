package dk.stockAnalyzer;

import yahoofinance.Stock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by aogj on 02-12-2015.
 */
public class StockFilterHelper {

    public static List<StockWrapper> removeUnwantedStocks(List<StockWrapper> stocks){

        List<StockWrapper> newStockList = new ArrayList<StockWrapper>();
        newStockList.addAll(stocks);


        for (StockWrapper stock : stocks) {

            try {

                BigDecimal marketCap = stock.getMarketCap();
                if (marketCap != null && marketCap.longValue() < 100_000_000L) {
                    newStockList.remove(stock);
                    System.out.println("Fjerner lav market cap: " + marketCap + "  " + stock.getSymbol());
                }

//                for (int i = 0 ; i < stock.getHistoricalValues().size() ; i++) {
//                    stock.getHistoricalValues().values().
//                }



            } catch (Exception e) {
                System.out.println("cannot remove unwanted stock. e=" + e);
            }


        }


        System.out.println("stocks removed=" + (stocks.size() - newStockList.size()) + ", before:" + stocks.size() + ", after:" + newStockList.size());

        return newStockList;

    }
}
