package dk.stockAnalyzer;

import java.util.List;
import java.util.SortedMap;

/**
 * Created by aogj on 11-09-2015.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        doRun(20, true, false, 20, 2, 4);
    }

    private static void doRun(int daysHistory, boolean usePersistedFile, boolean useTestStock, int daysBack, double weightFactorPlus, double weightFactorMnius) throws Exception {

        List<StockWrapper> stocks;
        if (usePersistedFile) {
            stocks = PortefolioPersister.load("PersistedStocks.bin");
        } else {
            stocks = YahooStockFetcher.getStockPortefolio(daysHistory);
            PortefolioPersister.persist(stocks, "PersistedStocks.bin");
        }

        if (useTestStock) {
            stocks.add(TestStock.getTestStock());
        }

        SortedMap<Double, StockWrapper> scoreMap = new AllansStrategy().getScores(daysBack, weightFactorPlus, weightFactorMnius, stocks);


        for (Double score : scoreMap.keySet()) {
            System.out.println("score: " + score + " - " + scoreMap.get(score).getName() + " stockHistory=" + scoreMap.get(score).getHistoricalValues());
        }

        GoogleStockCopyPasteLinkGenerator.doPrint(scoreMap);

    }

}
