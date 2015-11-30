package dk.stockAnalyzer;

import java.util.HashMap;

/**
 * Created by aogj on 27-09-2015.
 */
public class TestStock {

    public static StockWrapper getTestStock() {

        HashMap<Integer, Double> hm = new HashMap<Integer, Double>();
        hm.put(0, 100d);
        hm.put(1, 97d);
        hm.put(2, 95d);
        hm.put(3, 96d);
        hm.put(4, 93d);
        hm.put(5, 92d);
        hm.put(6, 90d);
        hm.put(7, 91d);
        hm.put(8, 93d);
        hm.put(9, 88d);
        hm.put(10, 88d);
        hm.put(11, 87d);
        hm.put(12, 89d);
        hm.put(13, 86d);
        hm.put(14, 90d);
        hm.put(15, 85d);
        hm.put(16, 85d);
        hm.put(17, 84d);
        hm.put(18, 85d);
        hm.put(19, 84d);
        hm.put(20, 85d);
        hm.put(21, 83d);
        hm.put(22, 89d);

        return new StockWrapper("test", "TEST", hm, null);

    }
}
