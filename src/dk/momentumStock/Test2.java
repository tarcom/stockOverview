package dk.momentumStock;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.Interval;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by aogj on 04-09-2015.
 */
public class Test2 {

    public static void main(String... args) throws Exception {
        doRun(4);
    }

    public static void doRun(int daysHist) throws Exception {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -10);

        String [] symbols = new String[] {"ABIGLMV.CO","ADMCAP-B.CO", "AFFI.CO", "ALIEUA.CO", "ALIGLO.CO", "ALIMTK.CO", "ALINA.CO", "ALK-B.CO", "ALMB.CO", "ALMBF-B.CO", "AM-B.CO", "AMBU-B.CO", "AOJ-P.CO", "ARKIL-B.CO", "ASGGRO.CO", "ATLA-DKK.CO", "AURI-B.CO", "BAIBA.CO", "BAIDK.CO", "BAIEEU.CO", "BAIEUA.CO", "BAIGE.CO", "BAIGIO.CO", "BAIHOJAK.CO", "BAIHOJLOK.CO", "BAIHRL.CO", "BAIKDO.CO", "BAIKDOB.CO", "BAILAT.CO", "BAILDO.CO", "BAILDOB.CO", "BAINEMA.CO", "BAIPB.CO", "BAIPEUAK.CO", "BAISTA.CO", "BAIUOB.CO", "BAIVIRAK.CO", "BAIVO.CO", "BAVA.CO", "BERLIV-B.CO", "BIAU.CO", "BIF.CO", "BIOPOR.CO", "BLVIS.CO", "BNORDIK-CSE.CO", "BO.CO", "BOCON-B.CO", "BORD-B.CO", "CAIDKA.CO", "CAIGLO.CO", "CARL-A.CO", "CARL-B.CO", "CBRAIN.CO", "CHEMM.CO", "CHR.CO", "CIMBER.CO", "COLO-B.CO", "COLUM.CO", "CPHCAP.CO", "CPHNW.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "DAB.CO", "DANSKE.CO", "DANT.CO", "DANTH.CO", "DEIEEA.CO", "DELTAQ.CO", "DFDS.CO", "DIBA.CO", "DJUR.CO", "DKC.CO", "DKIBIO.CO", "DKIDK.CO", "DKIDKIX.CO", "DKIEEU.CO", "DKIEIGO.CO", "DKIENGK4.CO", "DKIENGKEUO.CO", "DKIENGLO.CO", "DKIEU.CO", "DKIEUFOK.CO", "DKIFJE.CO", "DKIFJIX.CO", "DKIGLOIX2.CO", "DKIGLOP.CO", "DKIGLOSP.CO", "DKIGLOSP2.CO", "DKIJAP.CO", "DKIKI.CO", "DKILAT.CO", "DKINOIX.CO", "DKINYM.CO", "DKITEK.CO", "DKIUSA.CO", "DKTI.CO", "DLH.CO", "DNORD.CO", "DSV.CO", "EGE-B.CO", "EI.CO", "ELITE-B.CO", "ERRIA.CO", "EXP-B.CO", "EXQ.CO", "FED.CO", "FEI.CO", "FEII.CO", "FFARMS.CO", "FLS.CO", "FLUG-B.CO", "FO-AIR-CSE.CO", "FPEPI.CO", "FPLIM.CO", "FPMER.CO", "FPOPT.CO", "FPPAR.CO", "FPPEN.CO", "FPSAFE.CO", "FUISP.CO", "FYNBK.CO", "G4S.CO", "GABR.CO", "GEN.CO", "GERHSP-B.CO", "GES.CO", "GJ.CO", "GN.CO", "GRICLA.CO", "GRIEHY.CO", "GRINA.CO", "GRISEL.CO", "GRIUSHY.CO", "GRLA.CO", "GW.CO", "GYLD-A.CO", "GYLD-B.CO", "HAIDK.CO", "HAINOR.CO", "HAIVER.CO", "HARB-B.CO", "HART.CO", "HH.CO", "HOEJ-A.CO", "HOEJ-B.CO", "HVETBO.CO", "HVID.CO", "IC.CO", "IIIGLO.CO", "IIINEW.CO", "IMAIL-B.CO", "ISS.CO", "ISS-N.CO", "JDAN.CO", "JMI.CO", "JUTBK.CO", "JYIAKP.CO", "JYIDKA.CO", "JYIEUA.CO", "JYIFAV.CO", "JYIFJE.CO", "JYIGLO.CO", "JYIINA.CO", "JYIJAP.CO", "JYILAT.CO", "JYINOA.CO", "JYINYA.CO", "JYITYR.CO", "JYIUSA.CO", "JYSK.CO", "KBHL.CO", "KLEE-B.CO", "KRE.CO", "LASP.CO", "LAST-B.CO", "LL-A.CO", "LL-B.CO", "LOLB.CO", "LSIDK.CO", "LSIEU.CO", "LSILO.CO", "LSINA.CO", "LSIOBL.CO", "LSIVER.CO", "LUN.CO", "LUXOR-B.CO", "MAERSK-A.CO", "MAERSK-B.CO", "MAJAKT.CO", "MAJDKA.CO", "MAJDKO.CO", "MAJKON.CO", "MAJOBL.CO", "MAJPEN.CO", "MAJVAL.CO", "MATAS.CO", "MIGA-B.CO", "MNBA.CO", "MOLS.CO", "MT-B.CO", "NDA-DKK.CO", "NDIA.CO", "NDIAKTII.CO", "NDIDK.CO", "NDIDKA.CO", "NDIEM.CO", "NDIEU.CO", "NDIFJE.CO", "NDIGV.CO", "NDIHEA.CO", "NDIJAP.CO", "NDIUSA.CO", "NDIVER.CO", "NETOP.CO", "NEUR.CO", "NEWCAP.CO", "NGIVAL.CO", "NKT.CO", "NNIT.CO", "NORDIC.CO", "NORDJB.CO", "NORTHM.CO", "NOVO-B.CO", "NRDC.CO", "NRDF.CO", "NRSU.CO", "NTR-B.CO", "NUNA.CO", "NYIDA.CO", "NYIERO.CO", "NYIGLO.CO", "NYIKO.CO", "NYIKOB.CO", "NYILO.CO", "NYILOB.CO", "NZYM-B.CO", "OJBA.CO", "ONXEO.CO", "OSSR.CO", "OW.CO", "PARKEN.CO", "PNDORA.CO", "PRIMOF.CO", "PAAL-B.CO", "RBLN-B.CO", "RBREW.CO", "RELLA.CO", "RIAS-B.CO", "RILBA.CO", "ROCK-A.CO", "ROCK-B.CO", "ROV.CO", "RTX.CO", "SALB.CO", "SANI.CO", "SAS-DKK.CO", "SBS.CO", "SCD.CO", "SCHO.CO", "SEIDKA.CO", "SEIDAA.CO", "SEIEHU.CO", "SEIHYB.CO", "SEIIPK.CO", "SEIIPL.CO", "SEIIPM.CO", "SFG.CO", "SIF.CO", "SIM.CO", "SJGR.CO", "SKAKO.CO", "SKIAVK.CO", "SKIGLO.CO", "SKIKON.CO", "SKIVEK.CO", "SKJE.CO", "SMCAPDK.CO", "SOLAR-B.CO", "SPALOL.CO", "SPB.CO", "SPEAS.CO", "SPFA.CO", "SPG.CO", "SPICUM.CO", "SPIDJW.CO", "SPIDKA.CO", "SPIEUC.CO", "SPIEUG.CO", "SPIEUV.CO", "SPIJAG.CO", "SPIJAS.CO", "SPIJAV.CO", "SPIKOB.CO", "SPILOB.CO", "SPIMAK.CO", "SPIMAA.CO", "SPINOB.CO", "SPINOV.CO", "SPIUSG.CO", "SPIUSS.CO", "SPIUSV.CO", "SPIVA.CO", "SPIVAJ.CO", "SPIVUS.CO", "SPNO.CO", "SSILB.CO", "STRINV.CO", "STYLE.CO", "SVIESC.CO", "SYDB.CO", "SYIBRI.CO", "SYIBRIKAKK.CO", "SYIDK.CO", "SYIEU.CO", "SYIEUL.CO", "SYIFJE.CO", "SYIFJERNAKK.CO", "SYIIT.CO", "SYIKM.CO", "SYILAT.CO", "SYITYSKLAND.CO", "TDC.CO", "THRAN.CO", "TIV.CO", "TKDV.CO", "TNDR.CO", "TOP.CO", "TOPO.CO", "TORM-A.CO", "TOTA.CO", "TOWER.CO", "TPSL.CO", "TRIFOR.CO", "TRYG.CO", "UDV75.CO", "UIE.CO", "UPB.CO", "VAIBLUE.CO", "VAIGAK.CO", "VAIJAP.CO", "VEFY.CO", "VELO.CO", "VIBHK.CO", "VIINT.CO", "VIND.CO", "VIPRO.CO", "VJBA.CO", "VORD.CO", "VWS.CO", "WDH.CO", "ZEAL.CO", "AAB.CO"};


        Map<String, Stock> stocks = new HashMap<String, Stock>();
        for (int i = 0 ; i < symbols.length ; i++) {
            try {
                stocks.put(symbols[i], YahooFinance.get(symbols[i], cal, Interval.DAILY));
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }

        //Map<String, Stock> stocks = YahooFinance.get(symbols, cal, Interval.DAILY);

        TreeMap<Double, Stock> momentumMap = new TreeMap<Double, Stock>();
        for (Stock stock : stocks.values()) {
            Double histDiffProcent = ((stock.getHistory().get(0).getClose().doubleValue() / stock.getHistory().get(daysHist).getClose().doubleValue()) - 1) * 100;
            momentumMap.put(histDiffProcent, stock);
        }

        for (Stock stock : momentumMap.values()) {
            Double histDiffProcent = ((stock.getHistory().get(0).getClose().doubleValue() / stock.getHistory().get(daysHist).getClose().doubleValue()) - 1) * 100;

            String hist = "";
            for (int i = 0; i < daysHist; i++) {
                Double dayDiffProcent = ((stock.getHistory().get(i).getClose().doubleValue() / stock.getHistory().get(i + 1).getClose().doubleValue()) - 1) * 100;
                hist += roundStr(dayDiffProcent, 1) + "%, ";
            }

            System.out.println("(" + roundStr(histDiffProcent, 1) + "%) " + hist + stock.getName() + " (" + stock.getSymbol() + ")");
        }

    }


    public static String roundStr(double value, int places) {
        value = round(value, places);


        if (value < 0) {
            return String.valueOf(value);
        } else {
            return " " + String.valueOf(value);
        }
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
