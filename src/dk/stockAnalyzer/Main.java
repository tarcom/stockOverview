package dk.stockAnalyzer;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by aogj on 11-09-2015.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        boolean usePersistedFile = false;  // true = kør på data fra sidste kørsel (ingen Yahoo-fetch)
        int limit = 10000000;                   // sæt til -1 for fuld kørsel
        System.out.println("Welcome!");
        doRun(usePersistedFile, false, 200, 2, 2, limit);
        System.out.println("Bye!");
    }

    private static void doRun(boolean usePersistedFile, boolean useTestStock, int daysHistory, double weightFactorPlus, double weightFactorMnius, int limit) throws Exception {

        List<StockWrapper> stocks;
        if (usePersistedFile) {
            stocks = StockDb.load(daysHistory, limit);
        } else {
            stocks = YahooStockFetcher.getStockPortefolio(daysHistory, limit);
            StockDb.persist(stocks);
        }


        stocks = StockFilterHelper.removeUnwantedStocks(stocks);


        if (useTestStock) {
            stocks.add(TestStock.getTestStock());
        }

        SortedMap<Double, StockWrapper> scoreMap = new AllansStrategy().getScores(daysHistory, weightFactorPlus, weightFactorMnius, stocks);


        GoogleStockCopyPasteLinkGenerator.doPrint(scoreMap);


        SortedMap<Double, StockWrapper> top10scorMap = new TreeMap<Double, StockWrapper>();
        int count = 0;
        for (Double score : scoreMap.keySet()) {
            StockWrapper stock = scoreMap.get(score);
            if (count++ > scoreMap.size() - 10) {
                top10scorMap.put(score, stock);
            }
        }

        ExcelGenerator.doGenerate(top10scorMap);


    }

}
