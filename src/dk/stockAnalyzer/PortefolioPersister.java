package dk.stockAnalyzer;

import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * Created by aogj on 27-09-2015.
 */
public class PortefolioPersister {


    public static void persist(List<StockWrapper> stocks, String filename) throws Exception {

        FileOutputStream fos = new FileOutputStream(filename);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(stocks);
        oos.close();

        System.out.println("Persistet stocks to file " + filename);

    }

    public static List<StockWrapper> load(String filename) {
        try {
            System.out.println("loading file " + filename + "...");
            FileInputStream fis = null;
            fis = new FileInputStream(filename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            List<StockWrapper> stocks = (List<StockWrapper>) ois.readObject();
            ois.close();

            System.out.println("loaded stocks from file " + filename);

            return stocks;

        } catch (Exception e) {
            System.out.println("cannot load persisted portefolio from disk! filename=" + filename + ". " + e);
        }
        System.exit(0);
        return null;
    }

}
