package dk.techan;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes.Interval;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by aogj on 06-10-2015.
 */
public class HtmlGenerator {



    public static void main(String[] args) throws Exception {
        new HtmlGenerator().generateHtml("GEN.CO");
    }

    public void generateHtml(String... stocks) {

    }

    public void generateIndexHtml(String... stocks) throws Exception {
        PrintWriter writer = new PrintWriter("src\\dk\\techan\\index.html", "UTF-8");
        writer.println("<html><body><h1>Overview</h1>\n");

        for (String s : stocks) {
            writer.println("<a href=\"" + s + ".html\">" + s + ".html</a><br>");
        }

        writer.println("</body></html>");
        writer.close();
    }

    public void generateHtml(String stockStr) throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -100);

        Stock stock = YahooFinance.get(stockStr, cal, Interval.DAILY);
        PrintWriter writer = new PrintWriter("src\\dk\\techan\\data_"+stockStr+".csv", "UTF-8");
        writer.println("Date,Open,High,Low,Close,Volume");

        SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-yy");
        for (HistoricalQuote quote : stock.getHistory()) {
            String s = sdf.format(quote.getDate().getTime()).replaceAll("\\.", "");
            s += "," + quote.getOpen();
            s += "," + quote.getHigh();
            s += "," + quote.getLow();
            s += "," + quote.getClose();
            s += "," + quote.getVolume();
            System.out.println(s);

            writer.println(s);
        }
        writer.close();

    }

}
