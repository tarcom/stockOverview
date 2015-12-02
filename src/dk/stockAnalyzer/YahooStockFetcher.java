package dk.stockAnalyzer;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.Interval;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.util.*;

/**
 * Created by aogj on 11-09-2015.
 */
public class YahooStockFetcher {

    static int daysHistory;


    private static StockWrapper getStock(String name) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -daysHistory*2);
        try {
            Stock s = YahooFinance.get(name, cal, Interval.DAILY);
            StockWrapper sw = new StockWrapper(s.getName(), s.getSymbol(), getHistoryMapFromStock(s, daysHistory), getHistoryMapFromStock2(s, daysHistory), s.getStats().getMarketCap());
            return sw;
        } catch (Exception e) {
            System.out.println("Exception getting stock: " + name);
            e.printStackTrace();
            return null;
        }
    }


    static HashMap<Integer, Double> getHistoryMapFromStock(Stock stock, int daysHistory) throws Exception{
        HashMap<Integer, Double> historyMap = new HashMap<Integer, Double>();

        for (int i = 0; i < daysHistory + 2; i++) {
            historyMap.put(i, stock.getHistory().get(i).getClose().doubleValue());
        }
        return historyMap;
    }

    static HashMap<Integer, Calendar> getHistoryMapFromStock2(Stock stock, int daysHistory) throws Exception{
        HashMap<Integer, Calendar> historyMap = new HashMap<Integer, Calendar>();

        for (int i = 0; i < daysHistory + 2; i++) {
            historyMap.put(i, stock.getHistory().get(i).getDate());

        }
        return historyMap;
    }



    public static List<StockWrapper> getStockPortefolio(int daysHistory) {

        YahooStockFetcher.daysHistory = daysHistory;

        List<StockWrapper> stocks = new ArrayList<StockWrapper>();

        try {
            BufferedReader br = new BufferedReader(new FileReader("C:\\projects\\stockOverview\\doc\\allYahooStocks.txt"));
            String line = br.readLine();

            int i = 0;
            while (line != null) {
                line = br.readLine();
                stocks.add(getStock(line));
                if (i++ > 1000) {
                    //break;
                }
            }

            br.close();
        } catch (Exception e) {
            System.out.println(e);
        }



        //stocks.add(getStock("FING-B.ST"));


//        stocks.add(getStock("ABIGLMV.CO"));
//        stocks.add(getStock("ADMCAP-B.CO"));
//        stocks.add(getStock("AFFI.CO"));
//        stocks.add(getStock("ALIEUA.CO"));
//        stocks.add(getStock("ALIGLO.CO"));
//        stocks.add(getStock("ALIMTK.CO"));
//        stocks.add(getStock("ALINA.CO"));
//        stocks.add(getStock("ALK-B.CO"));
//        stocks.add(getStock("ALMB.CO"));
//        stocks.add(getStock("ALMBF-B.CO"));
//        stocks.add(getStock("AM-B.CO"));
//        stocks.add(getStock("AMBU-B.CO"));
//        stocks.add(getStock("AOJ-P.CO"));
//        stocks.add(getStock("ARKIL-B.CO"));
//        stocks.add(getStock("ASGGRO.CO"));
//        stocks.add(getStock("ATLA-DKK.CO"));
//        stocks.add(getStock("AURI-B.CO"));
//        stocks.add(getStock("BAIBA.CO"));
//        stocks.add(getStock("BAIDK.CO"));
//        stocks.add(getStock("BAIEEU.CO"));
//        stocks.add(getStock("BAIEUA.CO"));
//        stocks.add(getStock("BAIGE.CO"));
//        stocks.add(getStock("BAIGIO.CO"));
//        stocks.add(getStock("BAIHOJAK.CO"));
//        stocks.add(getStock("BAIHOJLOK.CO"));
//        stocks.add(getStock("BAIHRL.CO"));
//        stocks.add(getStock("BAIKDO.CO"));
//        stocks.add(getStock("BAIKDOB.CO"));
//        stocks.add(getStock("BAILAT.CO"));
//        stocks.add(getStock("BAILDO.CO"));
//        stocks.add(getStock("BAILDOB.CO"));
//        stocks.add(getStock("BAINEMA.CO"));
//        stocks.add(getStock("BAIPB.CO"));
//        stocks.add(getStock("BAIPEUAK.CO"));
//        stocks.add(getStock("BAISTA.CO"));
//        stocks.add(getStock("BAIUOB.CO"));
//        stocks.add(getStock("BAIVIRAK.CO"));
//        stocks.add(getStock("BAIVO.CO"));
//        stocks.add(getStock("BAVA.CO"));
//        stocks.add(getStock("BERLIV-B.CO"));
//        stocks.add(getStock("BIAU.CO"));
//        stocks.add(getStock("BIF.CO"));
//        stocks.add(getStock("BIOPOR.CO"));
//        stocks.add(getStock("BLVIS.CO"));
//        stocks.add(getStock("BNORDIK-CSE.CO"));
//        stocks.add(getStock("BO.CO"));
//        stocks.add(getStock("BOCON-B.CO"));
//        stocks.add(getStock("BORD-B.CO"));
//        stocks.add(getStock("CAIDKA.CO"));
//        stocks.add(getStock("CAIGLO.CO"));
//        stocks.add(getStock("CARL-A.CO"));
//        stocks.add(getStock("CARL-B.CO"));
//        stocks.add(getStock("CBRAIN.CO"));
//        stocks.add(getStock("CHEMM.CO"));
//        stocks.add(getStock("CHR.CO"));
//        stocks.add(getStock("CIMBER.CO"));
//        stocks.add(getStock("COLO-B.CO"));
//        stocks.add(getStock("COLUM.CO"));
//        stocks.add(getStock("CPHCAP.CO"));
//        stocks.add(getStock("CPHNW.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("Currency.CO"));
//        stocks.add(getStock("DAB.CO"));
//        stocks.add(getStock("DANSKE.CO"));
//        stocks.add(getStock("DANT.CO"));
//        stocks.add(getStock("DANTH.CO"));
//        stocks.add(getStock("DEIEEA.CO"));
//        stocks.add(getStock("DELTAQ.CO"));
//        stocks.add(getStock("DFDS.CO"));
//        stocks.add(getStock("DIBA.CO"));
//        stocks.add(getStock("DJUR.CO"));
//        stocks.add(getStock("DKC.CO"));
//        stocks.add(getStock("DKIBIO.CO"));
//        stocks.add(getStock("DKIDK.CO"));
//        stocks.add(getStock("DKIDKIX.CO"));
//        stocks.add(getStock("DKIEEU.CO"));
//        stocks.add(getStock("DKIEIGO.CO"));
//        stocks.add(getStock("DKIENGK4.CO"));
//        stocks.add(getStock("DKIENGKEUO.CO"));
//        stocks.add(getStock("DKIENGLO.CO"));
//        stocks.add(getStock("DKIEU.CO"));
//        stocks.add(getStock("DKIEUFOK.CO"));
//        stocks.add(getStock("DKIFJE.CO"));
//        stocks.add(getStock("DKIFJIX.CO"));
//        stocks.add(getStock("DKIGLOIX2.CO"));
//        stocks.add(getStock("DKIGLOP.CO"));
//        stocks.add(getStock("DKIGLOSP.CO"));
//        stocks.add(getStock("DKIGLOSP2.CO"));
//        stocks.add(getStock("DKIJAP.CO"));
//        stocks.add(getStock("DKIKI.CO"));
//        stocks.add(getStock("DKILAT.CO"));
//        stocks.add(getStock("DKINOIX.CO"));
//        stocks.add(getStock("DKINYM.CO"));
//        stocks.add(getStock("DKITEK.CO"));
//        stocks.add(getStock("DKIUSA.CO"));
//        stocks.add(getStock("DKTI.CO"));
//        stocks.add(getStock("DLH.CO"));
//        stocks.add(getStock("DNORD.CO"));
//        stocks.add(getStock("DSV.CO"));
//        stocks.add(getStock("EGE-B.CO"));
//        stocks.add(getStock("EI.CO"));
//        stocks.add(getStock("ELITE-B.CO"));
//        stocks.add(getStock("ERRIA.CO"));
//        stocks.add(getStock("EXP-B.CO"));
//        stocks.add(getStock("EXQ.CO"));
//        stocks.add(getStock("FED.CO"));
//        stocks.add(getStock("FEI.CO"));
//        stocks.add(getStock("FEII.CO"));
//        stocks.add(getStock("FFARMS.CO"));
//        stocks.add(getStock("FLS.CO"));
//        stocks.add(getStock("FLUG-B.CO"));
//        stocks.add(getStock("FO-AIR-CSE.CO"));
//        stocks.add(getStock("FPEPI.CO"));
//        stocks.add(getStock("FPLIM.CO"));
//        stocks.add(getStock("FPMER.CO"));
//        stocks.add(getStock("FPOPT.CO"));
//        stocks.add(getStock("FPPAR.CO"));
//        stocks.add(getStock("FPPEN.CO"));
//        stocks.add(getStock("FPSAFE.CO"));
//        stocks.add(getStock("FUISP.CO"));
//        stocks.add(getStock("FYNBK.CO"));
//        stocks.add(getStock("G4S.CO"));
//        stocks.add(getStock("GABR.CO"));
//        stocks.add(getStock("GEN.CO"));
//        stocks.add(getStock("GERHSP-B.CO"));
//        stocks.add(getStock("GES.CO"));
//        stocks.add(getStock("GJ.CO"));
//        stocks.add(getStock("GN.CO"));
//        stocks.add(getStock("GRICLA.CO"));
//        stocks.add(getStock("GRIEHY.CO"));
//        stocks.add(getStock("GRINA.CO"));
//        stocks.add(getStock("GRISEL.CO"));
//        stocks.add(getStock("GRIUSHY.CO"));
//        stocks.add(getStock("GRLA.CO"));
//        stocks.add(getStock("GW.CO"));
//        stocks.add(getStock("GYLD-A.CO"));
//        stocks.add(getStock("GYLD-B.CO"));
//        stocks.add(getStock("HAIDK.CO"));
//        stocks.add(getStock("HAINOR.CO"));
//        stocks.add(getStock("HAIVER.CO"));
//        stocks.add(getStock("HARB-B.CO"));
//        stocks.add(getStock("HART.CO"));
//        stocks.add(getStock("HH.CO"));
//        stocks.add(getStock("HOEJ-A.CO"));
//        stocks.add(getStock("HOEJ-B.CO"));
//        stocks.add(getStock("HVETBO.CO"));
//        stocks.add(getStock("HVID.CO"));
//        stocks.add(getStock("IC.CO"));
//        stocks.add(getStock("IIIGLO.CO"));
//        stocks.add(getStock("IIINEW.CO"));
//        stocks.add(getStock("IMAIL-B.CO"));
//        stocks.add(getStock("ISS.CO"));
//        stocks.add(getStock("ISS-N.CO"));
//        stocks.add(getStock("JDAN.CO"));
//        stocks.add(getStock("JMI.CO"));
//        stocks.add(getStock("JUTBK.CO"));
//        stocks.add(getStock("JYIAKP.CO"));
//        stocks.add(getStock("JYIDKA.CO"));
//        stocks.add(getStock("JYIEUA.CO"));
//        stocks.add(getStock("JYIFAV.CO"));
//        stocks.add(getStock("JYIFJE.CO"));
//        stocks.add(getStock("JYIGLO.CO"));
//        stocks.add(getStock("JYIINA.CO"));
//        stocks.add(getStock("JYIJAP.CO"));
//        stocks.add(getStock("JYILAT.CO"));
//        stocks.add(getStock("JYINOA.CO"));
//        stocks.add(getStock("JYINYA.CO"));
//        stocks.add(getStock("JYITYR.CO"));
//        stocks.add(getStock("JYIUSA.CO"));
//        stocks.add(getStock("JYSK.CO"));
//        stocks.add(getStock("KBHL.CO"));
//        stocks.add(getStock("KLEE-B.CO"));
//        stocks.add(getStock("KRE.CO"));
//        stocks.add(getStock("LASP.CO"));
//        stocks.add(getStock("LAST-B.CO"));
//        stocks.add(getStock("LL-A.CO"));
//        stocks.add(getStock("LL-B.CO"));
//        stocks.add(getStock("LOLB.CO"));
//        stocks.add(getStock("LSIDK.CO"));
//        stocks.add(getStock("LSIEU.CO"));
//        stocks.add(getStock("LSILO.CO"));
//        stocks.add(getStock("LSINA.CO"));
//        stocks.add(getStock("LSIOBL.CO"));
//        stocks.add(getStock("LSIVER.CO"));
//        stocks.add(getStock("LUN.CO"));
//        stocks.add(getStock("LUXOR-B.CO"));
//        stocks.add(getStock("MAERSK-A.CO"));
//        stocks.add(getStock("MAERSK-B.CO"));
//        stocks.add(getStock("MAJAKT.CO"));
//        stocks.add(getStock("MAJDKA.CO"));
//        stocks.add(getStock("MAJDKO.CO"));
//        stocks.add(getStock("MAJKON.CO"));
//        stocks.add(getStock("MAJOBL.CO"));
//        stocks.add(getStock("MAJPEN.CO"));
//        stocks.add(getStock("MAJVAL.CO"));
//        stocks.add(getStock("MATAS.CO"));
//        stocks.add(getStock("MIGA-B.CO"));
//        stocks.add(getStock("MNBA.CO"));
//        stocks.add(getStock("MOLS.CO"));
//        stocks.add(getStock("MT-B.CO"));
//        stocks.add(getStock("NDA-DKK.CO"));
//        stocks.add(getStock("NDIA.CO"));
//        stocks.add(getStock("NDIAKTII.CO"));
//        stocks.add(getStock("NDIDK.CO"));
//        stocks.add(getStock("NDIDKA.CO"));
//        stocks.add(getStock("NDIEM.CO"));
//        stocks.add(getStock("NDIEU.CO"));
//        stocks.add(getStock("NDIFJE.CO"));
//        stocks.add(getStock("NDIGV.CO"));
//        stocks.add(getStock("NDIHEA.CO"));
//        stocks.add(getStock("NDIJAP.CO"));
//        stocks.add(getStock("NDIUSA.CO"));
//        stocks.add(getStock("NDIVER.CO"));
//        stocks.add(getStock("NETOP.CO"));
//        stocks.add(getStock("NEUR.CO"));
//        stocks.add(getStock("NEWCAP.CO"));
//        stocks.add(getStock("NGIVAL.CO"));
//        stocks.add(getStock("NKT.CO"));
//        stocks.add(getStock("NNIT.CO"));
//        stocks.add(getStock("NORDIC.CO"));
//        stocks.add(getStock("NORDJB.CO"));
//        stocks.add(getStock("NORTHM.CO"));
//        stocks.add(getStock("NOVO-B.CO"));
//        stocks.add(getStock("NRDC.CO"));
//        stocks.add(getStock("NRDF.CO"));
//        stocks.add(getStock("NRSU.CO"));
//        stocks.add(getStock("NTR-B.CO"));
//        stocks.add(getStock("NUNA.CO"));
//        stocks.add(getStock("NYIDA.CO"));
//        stocks.add(getStock("NYIERO.CO"));
//        stocks.add(getStock("NYIGLO.CO"));
//        stocks.add(getStock("NYIKO.CO"));
//        stocks.add(getStock("NYIKOB.CO"));
//        stocks.add(getStock("NYILO.CO"));
//        stocks.add(getStock("NYILOB.CO"));
//        stocks.add(getStock("NZYM-B.CO"));
//        stocks.add(getStock("OJBA.CO"));
//        stocks.add(getStock("ONXEO.CO"));
//        stocks.add(getStock("OSSR.CO"));
//        stocks.add(getStock("OW.CO"));
//        stocks.add(getStock("PARKEN.CO"));
//        stocks.add(getStock("PNDORA.CO"));
//        stocks.add(getStock("PRIMOF.CO"));
//        stocks.add(getStock("PAAL-B.CO"));
//        stocks.add(getStock("RBLN-B.CO"));
//        stocks.add(getStock("RBREW.CO"));
//        stocks.add(getStock("RELLA.CO"));
//        stocks.add(getStock("RIAS-B.CO"));
//        stocks.add(getStock("RILBA.CO"));
//        stocks.add(getStock("ROCK-A.CO"));
//        stocks.add(getStock("ROCK-B.CO"));
//        stocks.add(getStock("ROV.CO"));
//        stocks.add(getStock("RTX.CO"));
//        stocks.add(getStock("SALB.CO"));
//        stocks.add(getStock("SANI.CO"));
//        stocks.add(getStock("SAS-DKK.CO"));
//        stocks.add(getStock("SBS.CO"));
//        stocks.add(getStock("SCD.CO"));
//        stocks.add(getStock("SCHO.CO"));
//        stocks.add(getStock("SEIDKA.CO"));
//        stocks.add(getStock("SEIDAA.CO"));
//        stocks.add(getStock("SEIEHU.CO"));
//        stocks.add(getStock("SEIHYB.CO"));
//        stocks.add(getStock("SEIIPK.CO"));
//        stocks.add(getStock("SEIIPL.CO"));
//        stocks.add(getStock("SEIIPM.CO"));
//        stocks.add(getStock("SFG.CO"));
//        stocks.add(getStock("SIF.CO"));
//        stocks.add(getStock("SIM.CO"));
//        stocks.add(getStock("SJGR.CO"));
//        stocks.add(getStock("SKAKO.CO"));
//        stocks.add(getStock("SKIAVK.CO"));
//        stocks.add(getStock("SKIGLO.CO"));
//        stocks.add(getStock("SKIKON.CO"));
//        stocks.add(getStock("SKIVEK.CO"));
//        stocks.add(getStock("SKJE.CO"));
//        stocks.add(getStock("SMCAPDK.CO"));
//        stocks.add(getStock("SOLAR-B.CO"));
//        stocks.add(getStock("SPALOL.CO"));
//        stocks.add(getStock("SPB.CO"));
//        stocks.add(getStock("SPEAS.CO"));
//        stocks.add(getStock("SPFA.CO"));
//        stocks.add(getStock("SPG.CO"));
//        stocks.add(getStock("SPICUM.CO"));
//        stocks.add(getStock("SPIDJW.CO"));
//        stocks.add(getStock("SPIDKA.CO"));
//        stocks.add(getStock("SPIEUC.CO"));
//        stocks.add(getStock("SPIEUG.CO"));
//        stocks.add(getStock("SPIEUV.CO"));
//        stocks.add(getStock("SPIJAG.CO"));
//        stocks.add(getStock("SPIJAS.CO"));
//        stocks.add(getStock("SPIJAV.CO"));
//        stocks.add(getStock("SPIKOB.CO"));
//        stocks.add(getStock("SPILOB.CO"));
//        stocks.add(getStock("SPIMAK.CO"));
//        stocks.add(getStock("SPIMAA.CO"));
//        stocks.add(getStock("SPINOB.CO"));
//        stocks.add(getStock("SPINOV.CO"));
//        stocks.add(getStock("SPIUSG.CO"));
//        stocks.add(getStock("SPIUSS.CO"));
//        stocks.add(getStock("SPIUSV.CO"));
//        stocks.add(getStock("SPIVA.CO"));
//        stocks.add(getStock("SPIVAJ.CO"));
//        stocks.add(getStock("SPIVUS.CO"));
//        stocks.add(getStock("SPNO.CO"));
//        stocks.add(getStock("SSILB.CO"));
//        stocks.add(getStock("STRINV.CO"));
//        stocks.add(getStock("STYLE.CO"));
//        stocks.add(getStock("SVIESC.CO"));
//        stocks.add(getStock("SYDB.CO"));
//        stocks.add(getStock("SYIBRI.CO"));
//        stocks.add(getStock("SYIBRIKAKK.CO"));
//        stocks.add(getStock("SYIDK.CO"));
//        stocks.add(getStock("SYIEU.CO"));
//        stocks.add(getStock("SYIEUL.CO"));
//        stocks.add(getStock("SYIFJE.CO"));
//        stocks.add(getStock("SYIFJERNAKK.CO"));
//        stocks.add(getStock("SYIIT.CO"));
//        stocks.add(getStock("SYIKM.CO"));
//        stocks.add(getStock("SYILAT.CO"));
//        stocks.add(getStock("SYITYSKLAND.CO"));
//        stocks.add(getStock("TDC.CO"));
//        stocks.add(getStock("THRAN.CO"));
//        stocks.add(getStock("TIV.CO"));
//        stocks.add(getStock("TKDV.CO"));
//        stocks.add(getStock("TNDR.CO"));
//        stocks.add(getStock("TOP.CO"));
//        stocks.add(getStock("TOPO.CO"));
//        stocks.add(getStock("TORM-A.CO"));
//        stocks.add(getStock("TOTA.CO"));
//        stocks.add(getStock("TOWER.CO"));
//        stocks.add(getStock("TPSL.CO"));
//        stocks.add(getStock("TRIFOR.CO"));
//        stocks.add(getStock("TRYG.CO"));
//        stocks.add(getStock("UDV75.CO"));
//        stocks.add(getStock("UIE.CO"));
//        stocks.add(getStock("UPB.CO"));
//        stocks.add(getStock("VAIBLUE.CO"));
//        stocks.add(getStock("VAIGAK.CO"));
//        stocks.add(getStock("VAIJAP.CO"));
//        stocks.add(getStock("VEFY.CO"));
//        stocks.add(getStock("VELO.CO"));
//        stocks.add(getStock("VIBHK.CO"));
//        stocks.add(getStock("VIINT.CO"));
//        stocks.add(getStock("VIND.CO"));
//        stocks.add(getStock("VIPRO.CO"));
//        stocks.add(getStock("VJBA.CO"));
//        stocks.add(getStock("VORD.CO"));
//        stocks.add(getStock("VWS.CO"));
//        stocks.add(getStock("WDH.CO"));
//        stocks.add(getStock("ZEAL.CO"));
//        stocks.add(getStock("AAB.CO"));

        stocks.removeAll(Collections.singleton(null));

        return stocks;
    }

}
