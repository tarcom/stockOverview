package dk.stockAnalyzer;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.SortedMap;

/**
 * Created by aogj on 29-11-2015.
 */
public class ExcelGenerator {

    public static SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");

    public static void main(String[] args) throws Exception {
        //doGenerate();
    }

    static void doGenerate(SortedMap<Double, StockWrapper> sortedMap) throws Exception {

        if (sortedMap.size() == 0) {
            System.out.println("sorted map empty!!");
            return;
        }


        int numberOfDates = sortedMap.get(sortedMap.firstKey()).getHistoricalValues().size();
        int numberOfStocks = sortedMap.size();
        String[][] matrix = new String[numberOfStocks + 1][numberOfDates + 3];




        //insert first history days
        int y = 0;
        int x = 0;
        matrix[x][y++] = "score:";
        matrix[x][y++] = "name:";
        matrix[x][y++] = "symbol:";
        for (int day : sortedMap.get(sortedMap.firstKey()).getHistoricalValues2().keySet()) {
            Calendar cal = sortedMap.get(sortedMap.firstKey()).getHistoricalValues2().get(day);
            matrix[x][y++] = String.valueOf(sdf.format(cal.getTime()));
        }




        //generate the matrix

        x = 1;
        for (double score : sortedMap.keySet()) {
            y = 0;
            StockWrapper stockWrapper = sortedMap.get(score);

            matrix[x][y++] = String.valueOf(score);
            matrix[x][y++] = stockWrapper.getName();
            matrix[x][y++] = stockWrapper.getSymbol();

            for (Double d : stockWrapper.getHistoricalValues().values()) {
                //matrix[x][y++] = String.valueOf(d);
                double base100index = calculateBase0Index(d, stockWrapper.getHistoricalValues().get(stockWrapper.getHistoricalValues().size() - 1));
                matrix[x][y++] = String.valueOf(base100index);
            }

            x++;
        }




        // Write the matrix to file...

        String filename = "C:\\Projects\\stockOverview\\excelGenerator.csv";
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
