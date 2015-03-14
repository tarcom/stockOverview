package dk.skov;

import java.util.Calendar;
import java.util.Date;
import java.util.TreeMap;

/**
 * Created by aogj on 27-01-14.
 */
public class Util {

    public static Calendar calendarFor(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();

        Date date = new Date(year-1900, month, day);

        cal.setTime(date);
        return cal;
    }

    public static StringBuffer printNice( TreeMap<Calendar, Double> historicValues){
        StringBuffer sb = new StringBuffer();
        for(Calendar c : historicValues.keySet()){
            sb.append(c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH) + "-" + c.get(Calendar.DAY_OF_MONTH) + " : " + historicValues.get(c) + "\n");
        }
    return sb;

    }
}
