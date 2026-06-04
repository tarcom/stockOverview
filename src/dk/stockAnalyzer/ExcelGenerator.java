package dk.stockAnalyzer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.SortedMap;

/**
 * Created by aogj on 29-11-2015.
 */
public class ExcelGenerator {

    public static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

    public static void main(String[] args) throws Exception {
        //doGenerate();
    }

    static void doGenerate(SortedMap<Double, StockWrapper> sortedMap) throws Exception {

        if (sortedMap.size() == 0) {
            System.out.println("sorted map empty!!");
            return;
        }


        // Historik-længder kan variere pr. aktie (≥70%-tærsklen), så dimensionér
        // efter den længste, og brug dens datoer som header (index 0 = nyeste).
        int numberOfDates = 0;
        StockWrapper longest = null;
        for (StockWrapper sw : sortedMap.values()) {
            int len = sw.getHistoricalValues().size();
            if (len > numberOfDates) { numberOfDates = len; longest = sw; }
        }
        int numberOfStocks = sortedMap.size();
        String[][] matrix = new String[numberOfStocks + 1][numberOfDates + 3];


        //insert first history days
        int y = 0;
        int x = 0;
        matrix[x][y++] = "score:";
        matrix[x][y++] = "name:";
        matrix[x][y++] = "symbol:";
        for (int day = 0; day < numberOfDates; day++) {
            Calendar cal = longest.getHistoricalValues2().get(day);
            matrix[x][y++] = cal != null ? String.valueOf(sdf.format(cal.getTime())) : "";
        }




        //generate the matrix

        x = 1;
        for (double score : sortedMap.keySet()) {
            y = 0;
            StockWrapper stockWrapper = sortedMap.get(score);
            HashMap<Integer, Double> values = stockWrapper.getHistoricalValues();

            matrix[x][y++] = String.valueOf(score);
            matrix[x][y++] = stockWrapper.getName();
            matrix[x][y++] = stockWrapper.getSymbol();

            Double base = values.get(values.size() - 1); // ældste = base
            for (int day = 0; day < numberOfDates; day++) {
                Double d = values.get(day);
                if (d != null && base != null) {
                    matrix[x][y++] = String.valueOf(calculateBase0Index(d, base));
                } else {
                    matrix[x][y++] = ""; // aktien har ikke data så langt tilbage
                }
            }

            x++;
        }




        // Write the matrix to file...

        new File("output").mkdirs();
        String filename = "output" + File.separator + "stockScreener.csv";
        PrintWriter writer = new PrintWriter(new FileOutputStream(filename));
        for (y = 0; y < numberOfDates + 3; y++) {

            String line = "";
            for (x = 0 ; x < numberOfStocks + 1 ; x++) {

                line += matrix[x][y] + ";";

            }

            if (y != 1) {
                line = line.replace('.', ',');
            }
            writer.println(line);
            x++;
        }
        writer.close();
        System.out.println("done. writer=" + filename);

    }

    static double calculateBase0Index(double value, double base100) {
        return ((value / base100) -1 ) * 100;
    }

}
