package dk.stockAnalyzer;

import yahoofinance.Stock;

import java.util.*;

/**
 * Created by aogj on 11-09-2015.
 */
public class AllansStrategy {

    private static boolean printDebug = false;

    public SortedMap<Double, StockWrapper> getScores(int daysBack, double weightFactorPlus, double weightFactorMnius, List<StockWrapper> stocks) {
        Random rand = new Random();
        SortedMap<Double, StockWrapper> sortedStocks = new TreeMap<Double, StockWrapper>();
        for (StockWrapper stock : stocks) {
            if (stock.getSymbol().equals("AMCO")) {
                String s = "";
                s += "asd";
            }
            double score = getScore(daysBack, weightFactorPlus, weightFactorMnius, stock.getHistoricalValues());
            if (score == -111) {
                System.out.println("removing stock from portefolio as it exceeds +/- 100& in one day! stock=" + stock.getName() + " - " + stock.getSymbol());
            } else if (score == 0.0) {
                // frosne priser — alle dage er 0% ændring, ingen signal
            } else {
                double smallRandom = rand.nextDouble() / 1000000;
                score += smallRandom;
                sortedStocks.put(score, stock);
            }
        }

        System.out.println("getScore, input map size=" + stocks.size() + ", output score map size=" + sortedStocks.size());

        return sortedStocks;
    }

    public double getScore(int daysBack, double weightFactorPlus, double weightFactorMnius, HashMap<Integer, Double> historicalValues) {

        // Afgræns til den faktisk tilgængelige historik: vi læser get(i) og get(i+1),
        // så vi kan score over højst (size-1) dage. Aktier med kort historik (≥70%,
        // se YahooStockFetcher.MIN_HISTORY_FRACTION) scorer derfor over færre dage —
        // derfor normaliseres scoren bagefter, så lange og korte historikker er
        // sammenlignelige (gennemsnitlig vægtet dagsbevægelse i stedet for sum).
        int effectiveDays = Math.min(daysBack, historicalValues.size() - 1);
        if (effectiveDays < 2) {
            return 0.0; // for lidt til et signal
        }

        Double score = 0d;
        for (int i = 0; i < effectiveDays; i++) {
            //double currentWeightFactorPlus = (weightFactorPlus / daysBack) * (daysBack - i);
            double currentWeightFactorPlus = (((effectiveDays-1) - i*1d) / (effectiveDays-1)*1d) * (weightFactorPlus-1*1d) + 1;
            double currentWeightFactorMinusMultiplyer = currentWeightFactorPlus * weightFactorMnius;

            if(printDebug) {
                //System.out.println("getScore: i=" + i + ", currentWeightFactorPlus=" + currentWeightFactorPlus + ", currentWeightFactorMinusMultiplyer=" + currentWeightFactorMinusMultiplyer);
                System.out.printf("getScore: i=%3d, currentWeightFactorPlus=%2.1f, currentWeightFactorMinusMultiplyer=%2.1f%n", i, currentWeightFactorPlus, currentWeightFactorMinusMultiplyer);
            }

            double histDiffPct = ((historicalValues.get(i) / historicalValues.get(i + 1)) - 1) * 100;

            if (histDiffPct > 100) {
                System.out.println("share exceeded 100% in one day! histDiffPct=" + histDiffPct);
                return -111;
            } else if (histDiffPct < -100) {
                System.out.println("share exceeded -100% in one day! histDiffPct=" + histDiffPct);
                return -111;
            }

            if (histDiffPct > 0) {
                //plus
                double value = histDiffPct * currentWeightFactorPlus;
                score += value;
            } else {
                //minus
                double value = histDiffPct * currentWeightFactorMinusMultiplyer;
                score += value;
            }
        }

        printDebug = false;

        // Normaliser: gennemsnitlig vægtet dagsbevægelse, så scoren ikke skalerer med
        // antal dage (ellers ville lang historik systematisk slå kort historik).
        return score / effectiveDays;
    }


}
