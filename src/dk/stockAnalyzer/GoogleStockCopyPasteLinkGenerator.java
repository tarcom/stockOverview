package dk.stockAnalyzer;

import java.util.*;

public class GoogleStockCopyPasteLinkGenerator {

    private static final Map<String, String> SUFFIX_TO_EXCHANGE = new LinkedHashMap<>();
    static {
        SUFFIX_TO_EXCHANGE.put(".CO",  "Nasdaq Copenhagen");
        SUFFIX_TO_EXCHANGE.put(".ST",  "Nasdaq Stockholm");
        SUFFIX_TO_EXCHANGE.put(".OL",  "Oslo Børs");
        SUFFIX_TO_EXCHANGE.put(".HE",  "Nasdaq Helsinki");
        SUFFIX_TO_EXCHANGE.put(".L",   "London");
        SUFFIX_TO_EXCHANGE.put(".PA",  "Euronext Paris");
        SUFFIX_TO_EXCHANGE.put(".AS",  "Euronext Amsterdam");
        SUFFIX_TO_EXCHANGE.put(".MI",  "Borsa Italiana");
        SUFFIX_TO_EXCHANGE.put(".F",   "Frankfurt");
        SUFFIX_TO_EXCHANGE.put(".DE",  "Xetra");
        SUFFIX_TO_EXCHANGE.put(".AX",  "ASX");
        SUFFIX_TO_EXCHANGE.put(".HK",  "HKEX");
        SUFFIX_TO_EXCHANGE.put(".T",   "Tokyo");
        SUFFIX_TO_EXCHANGE.put(".TO",  "Toronto");
        SUFFIX_TO_EXCHANGE.put(".MC",  "Madrid");
        SUFFIX_TO_EXCHANGE.put(".BR",  "Euronext Brussels");
        SUFFIX_TO_EXCHANGE.put(".VI",  "Vienna");
        SUFFIX_TO_EXCHANGE.put(".LS",  "Euronext Lisbon");
        SUFFIX_TO_EXCHANGE.put(".SZ",  "Shenzhen");
        SUFFIX_TO_EXCHANGE.put(".SS",  "Shanghai");
        SUFFIX_TO_EXCHANGE.put(".KS",  "Korea");
        SUFFIX_TO_EXCHANGE.put(".SA",  "B3 Brazil");
        SUFFIX_TO_EXCHANGE.put(".NZ",  "NZX");
        SUFFIX_TO_EXCHANGE.put(".KL",  "Bursa Malaysia");
        SUFFIX_TO_EXCHANGE.put(".KS",  "Korea Exchange");
        SUFFIX_TO_EXCHANGE.put(".SZ",  "Shenzhen");
        SUFFIX_TO_EXCHANGE.put(".SS",  "Shanghai");
    }

    private static String exchange(String symbol) {
        for (Map.Entry<String, String> e : SUFFIX_TO_EXCHANGE.entrySet()) {
            if (symbol.endsWith(e.getKey())) return e.getValue();
        }
        return "US";
    }

    private static String yahooLink(String symbol) {
        return "https://finance.yahoo.com/quote/" + symbol;
    }

    private static String periodReturn(StockWrapper stock) {
        Map<Integer, Double> h = stock.getHistoricalValues();
        if (h == null || h.size() < 2) return "  n/a";
        double newest = h.get(0);
        double oldest = h.get(h.size() - 1);
        if (oldest == 0) return "  n/a";
        double pct = (newest / oldest - 1) * 100;
        return String.format("%+6.1f%%", pct);
    }

    public static void doPrint(SortedMap<Double, StockWrapper> scoreMap) {
        if (scoreMap.isEmpty()) return;

        System.out.println("\n=== TOP 10 ===");
        System.out.printf("%-3s  %-8s  %-7s  %-35s  %-22s  %s%n",
                "#", "Score", "Afkast", "Navn", "Børs", "Link");
        System.out.println("-".repeat(110));

        int rank = 1;
        List<Double> keys = new ArrayList<>(scoreMap.keySet());
        for (int i = keys.size() - 1; i >= 0 && rank <= 10; i--, rank++) {
            double score    = keys.get(i);
            StockWrapper sw = scoreMap.get(score);
            System.out.printf("#%-2d  %8.1f  %s  %-35s  %-22s  %s%n",
                    rank,
                    score,
                    periodReturn(sw),
                    truncate(sw.getName(), 35),
                    exchange(sw.getSymbol()),
                    yahooLink(sw.getSymbol()));
        }
        System.out.println();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
