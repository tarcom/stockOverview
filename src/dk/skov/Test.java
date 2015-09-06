package dk.skov;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by aogj on 31-07-2015.
 */
public class Test {

    public static SimpleDateFormat sdf = new SimpleDateFormat("d-MMM-YY");

    public static void main(String[] s) throws Exception {
        test("TSLA");
    }
    public static void test(String stock) throws Exception {


        String current = new java.io.File( "." ).getCanonicalPath();
        System.out.println("Current dir:"+current);

        String currentDir = System.getProperty("user.dir");
        System.out.println("Current dir using System:" +currentDir);


        Stock tesla = YahooFinance.get(stock, true);
        System.out.println(tesla.getHistory());



        //PrintWriter pw = new PrintWriter(new FileOutputStream(nameOfTextFile));
        PrintWriter writer = new PrintWriter(new FileOutputStream("C:\\Projects\\stockOverview\\out\\artifacts\\stocks3_war_exploded\\data.csv"));
        writer.println("Date,Open,High,Low,Close,Volume");
        for (HistoricalQuote hq : tesla.getHistory()) {

            String str = "";

            str += sdf.format(hq.getDate().getTime()) + ",";
            str += hq.getOpen() + ",";
            str += hq.getHigh() + ",";
            str += hq.getLow() + ",";
            str += hq.getClose() + ",";
            str += hq.getVolume();

            writer.println(str);

        }
        writer.close();
        System.out.println("done. writer=" + writer.toString());

        String current2 = new java.io.File( "data.csv" ).getCanonicalPath();
        System.out.println("Current2 dir:"+current2);


    }



}
