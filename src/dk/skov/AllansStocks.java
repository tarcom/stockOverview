package dk.skov;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Created by aogj on 25-01-14.
 */
public class AllansStocks {

    public static void main(String... args) throws Exception {
        new AllansStocks().doGenerate();
    }

    public void doGenerate() throws Exception {

        YahooStockFetcher yahooStockFetcher = new YahooStockFetcher();

        ArrayList<StockWrapper> stocks = new ArrayList<StockWrapper>();

        Calendar startDate = Util.calendarFor(2007, Calendar.JANUARY, 1);

        stocks.add(new StockWrapper("AAPL", yahooStockFetcher.doGet("AAPL", startDate), Util.calendarFor(2014, Calendar.JANUARY, 22)));
        stocks.add(new StockWrapper("DANSKE.CO", yahooStockFetcher.doGet("DANSKE.CO", startDate), Util.calendarFor(2009, Calendar.APRIL, 27)));
        stocks.add(new StockWrapper("NRKBF", yahooStockFetcher.doGet("NRKBF", startDate), Util.calendarFor(2010, Calendar.APRIL, 9))); //funny start date...
        stocks.add(new StockWrapper("NZYM-B.CO", yahooStockFetcher.doGet("NZYM-B.CO", startDate), Util.calendarFor(2010, Calendar.JULY, 30)));

        System.out.println("stocks size is " + stocks.size());

        HtmlChartGenerator htmlChartGenerator = new HtmlChartGenerator();
        StringBuffer sb = htmlChartGenerator.doGenerate(stocks, true);
        //sb.append(htmlChartGenerator.doGenerate(stocks, false));
        htmlChartGenerator.doWriteFile("googleGraph.html", sb);
        System.out.println("bye");

    }

}
