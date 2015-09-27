package dk.stockAnalyzer;

import java.util.List;
import java.util.SortedMap;

/**
 * Created by aogj on 11-09-2015.
 */
public class Main {

    public static void main(String[] args) throws Exception {

        doRun(20);
    }


    private static void doRun(int daysHistory) throws Exception {

        //List<StockWrapper> stocks = YahooStockFetcher.getStockPortefolio(daysHistory);
        //PortefolioPersister.persist(stocks, "PersistedStocks.bin");

        List<StockWrapper> stocks = PortefolioPersister.load("PersistedStocks.bin");


        //stocks.add(TestStock.getTestStock());

        SortedMap<Double, StockWrapper> scoreMap = new AllansStrategy().getScores(20, 1, 1, stocks);


        for (Double score : scoreMap.keySet()) {
            System.out.println("score: " + score + " - " + scoreMap.get(score).getName() + " stockHistory=" + scoreMap.get(score).getHistoricalValues());
        }

        GoogleStockCopyPasteLinkGenerator.doPrint(scoreMap);

    }

}
