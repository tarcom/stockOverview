/*
 * Created on 12.12.2009
 *
 * (c) Copyright Christian P. Fries, Germany. All rights reserved. Contact: email@christian-fries.de.
 */
package dk.skov;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TreeMap;


/**
 * @author Allan Skov
 */
public class YahooStockFetcher {

    public static DateFormat df = new SimpleDateFormat("yyyy-MM-dd");

    /*
    public static void main(String[] args) {
        try {
            YahooStockFetcher fetcher = new YahooStockFetcher();

            StockWrapper aapl = fetcher.doGet("AAPL", Util.calendarFor(2013, Calendar.APRIL, 19), Util.calendarFor(2012, Calendar.JANUARY, 01));

            System.out.println("aapl.size=" + aapl.getHistoricValues().size());
            for (Calendar c : aapl.getHistoricValues().keySet()) {
                System.out.println(c.getTime() + " - " + aapl.getHistoricValues().get(c));
            }
            System.out.println("bye");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    */

    public TreeMap<Calendar, Double> doGet(String symbol, Calendar startDate) throws Exception {

        Calendar today = Calendar.getInstance();

        URL ulr = new URL("http://ichart.finance.yahoo.com/table.csv?s=" +
                symbol + "&a=" +
                startDate.get(Calendar.MONTH) + "&b=" +
                startDate.get(Calendar.DAY_OF_MONTH) + "&c=" +
                startDate.get(Calendar.YEAR) + "&d=" +
                today.get(Calendar.MONTH) + "&e=" +
                today.get(Calendar.DAY_OF_MONTH) + "&f=" +
                today.get(Calendar.YEAR) + "&g=d&ignore=.csv");

        System.out.println("fetching " + ulr.toString());
        URLConnection urlConnection = ulr.openConnection();
        BufferedReader reader = null;

        TreeMap<Calendar, Double> stockDateValues = new TreeMap<Calendar, Double>();

        reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        String inputLine;
        reader.readLine(); //first line titles is ignored
        while ((inputLine = reader.readLine()) != null) {
            String[] yahooStockInfo = inputLine.split(",");

            String dateStr = yahooStockInfo[0];
            java.util.Date date = df.parse(dateStr);
            Calendar c = Calendar.getInstance();
            c.setTime(date);
            stockDateValues.put(c, Double.valueOf(yahooStockInfo[1]));
        }

        reader.close();

        return stockDateValues;
    }


}
