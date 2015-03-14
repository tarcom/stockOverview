package dk.skov;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeSet;

/**
 * Created by aogj on 24-01-14.
 */
public class HtmlChartGenerator {

    public void doWriteFile(String filename, StringBuffer content) throws Exception {
        PrintWriter writer = new PrintWriter(filename, "UTF-8");

        writer.println("<html>\n" +
                "<head>\n");

        writer.println(content);

        writer.println(
                "</head>\n" +
                        "<body>\n" +
                        "<div id=\"chart_div\"></div>\n" +
                        "</body>\n" +
                        "</html>");

        writer.close();
    }

    public StringBuffer doGenerate(ArrayList<StockWrapper> stocks, boolean index100based) throws Exception {
        StringBuffer sb = new StringBuffer();
        sb.append("    <script type=\"text/javascript\" src=\"https://www.google.com/jsapi\"></script>\n" +
                "    <script type=\"text/javascript\">\n" +
                "      google.load(\"visualization\", \"1\", {packages:[\"corechart\"]});\n" +
                "      google.setOnLoadCallback(drawChart);\n" +
                "      function drawChart() {\n" +
                "    // Create and populate the data table.\n" +
                "    var dateData = new google.visualization.DataTable();\n" +
                "    dateData.addColumn('date', 'Date');\n");

        System.out.println("stocks size is " + stocks.size());

        for (StockWrapper stock : stocks) {
            sb.append("    dateData.addColumn('number', '" + stock.getSymbol() + " - " + stock.getIndex100Value() + "');\n");
            sb.append("    dateData.addColumn('number', '" + stock.getSymbol() + " - " + stock.getIndex100Value() + "');\n");
        }

        sb.append("    dateData.addRows([");

        for (String line : getGraphString(stocks, index100based)) {
            sb.append(line);
        }

        //writer.println("[new Date(2014,01,23), 1],\n");
        //writer.println("[new Date(2014,01,24), 2],\n");
        //writer.println("[new Date(2014,01,25), 3],\n");
        //writer.println("[new Date(2014,01,29), 4],\n");

        sb.append("      ]);\n" +
                "\n" +
                "    var continuousOptions = {\n" +
                //"      interpolateNulls = true,\n" +
                "      title: 'Allans stocks',\n" +
                "      width: 1400, height: 800,\n" +
                "      legend: 'taadaa',\n" +
                "      colors: ['red', 'pink', 'green', 'lightgreen', 'yellow', 'lightyellow', 'blue', 'lightblue'],\n" +
                "      curveType: 'function',\n" +
                "      pointSize: 0\n" +
                "    };\n" +
                "\n" +
                "    var continuousDateChart = new google.visualization.LineChart(document.getElementById('chart_div'));\n" +
                "    continuousDateChart.draw(dateData, continuousOptions);\n" +
                "\n" +
                "  }\n" +
                "\n" +
                "\n" +
                "    </script>\n");


        return sb;
    }

    private ArrayList<String> getGraphString(ArrayList<StockWrapper> stocks, boolean index100based) {
        ArrayList<String> graphStrings = new ArrayList<String>();

        TreeSet<Calendar> allDates = new TreeSet<Calendar>();
        for (StockWrapper stock : stocks) {
            allDates.addAll(stock.getHistoricValues().keySet());
        }

        for (Calendar oneDate : allDates) {
            String graphString;
            graphString = "[new Date(" +
                    oneDate.get(Calendar.YEAR) + "," +
                    oneDate.get(Calendar.MONTH) + "," +
                    oneDate.get(Calendar.DAY_OF_MONTH) + ")";

            for (StockWrapper stock : stocks) {
                if (index100based) {
                    graphString += ", " + stock.getHistoricValuesIndex100AfterBuyDate().get(oneDate);
                    graphString += ", " + stock.getHistoricValuesIndex100BeforeBuyDate().get(oneDate);
                } else {
                    graphString += ", " + stock.getHistoricValuesAfterBuyDate().get(oneDate);
                    graphString += ", " + stock.getHistoricValuesBeforeBuyDate().get(oneDate);
                }
            }
            graphString += "],\n";
            graphStrings.add(graphString);
        }

        return graphStrings;
    }
}
