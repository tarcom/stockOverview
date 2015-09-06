package dk.momentumStock;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import yahoofinance.histquotes.Interval;

import java.io.IOException;
import java.util.*;

/**
 * Created by aogj on 04-09-2015.
 */
public class Test2 {

    public static void main(String... args) throws Exception {
        doRun(4);
    }

    public static void doRun(int daysHist) throws Exception {


        //String [] symbols = new String[] {"ABIGLMV.CO","ADMCAP-B.CO", "AFFI.CO", "ALIEUA.CO", "ALIGLO.CO", "ALIMTK.CO", "ALINA.CO", "ALK-B.CO", "ALMB.CO", "ALMBF-B.CO", "AM-B.CO", "AMBU-B.CO", "AOJ-P.CO", "ARKIL-B.CO", "ASGGRO.CO", "ATLA-DKK.CO", "AURI-B.CO", "BAIBA.CO", "BAIDK.CO", "BAIEEU.CO", "BAIEUA.CO", "BAIGE.CO", "BAIGIO.CO", "BAIHOJAK.CO", "BAIHOJLOK.CO", "BAIHRL.CO", "BAIKDO.CO", "BAIKDOB.CO", "BAILAT.CO", "BAILDO.CO", "BAILDOB.CO", "BAINEMA.CO", "BAIPB.CO", "BAIPEUAK.CO", "BAISTA.CO", "BAIUOB.CO", "BAIVIRAK.CO", "BAIVO.CO", "BAVA.CO", "BERLIV-B.CO", "BIAU.CO", "BIF.CO", "BIOPOR.CO", "BLVIS.CO", "BNORDIK-CSE.CO", "BO.CO", "BOCON-B.CO", "BORD-B.CO", "CAIDKA.CO", "CAIGLO.CO", "CARL-A.CO", "CARL-B.CO", "CBRAIN.CO", "CHEMM.CO", "CHR.CO", "CIMBER.CO", "COLO-B.CO", "COLUM.CO", "CPHCAP.CO", "CPHNW.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "Currency.CO", "DAB.CO", "DANSKE.CO", "DANT.CO", "DANTH.CO", "DEIEEA.CO", "DELTAQ.CO", "DFDS.CO", "DIBA.CO", "DJUR.CO", "DKC.CO", "DKIBIO.CO", "DKIDK.CO", "DKIDKIX.CO", "DKIEEU.CO", "DKIEIGO.CO", "DKIENGK4.CO", "DKIENGKEUO.CO", "DKIENGLO.CO", "DKIEU.CO", "DKIEUFOK.CO", "DKIFJE.CO", "DKIFJIX.CO", "DKIGLOIX2.CO", "DKIGLOP.CO", "DKIGLOSP.CO", "DKIGLOSP2.CO", "DKIJAP.CO", "DKIKI.CO", "DKILAT.CO", "DKINOIX.CO", "DKINYM.CO", "DKITEK.CO", "DKIUSA.CO", "DKTI.CO", "DLH.CO", "DNORD.CO", "DSV.CO", "EGE-B.CO", "EI.CO", "ELITE-B.CO", "ERRIA.CO", "EXP-B.CO", "EXQ.CO", "FED.CO", "FEI.CO", "FEII.CO", "FFARMS.CO", "FLS.CO", "FLUG-B.CO", "FO-AIR-CSE.CO", "FPEPI.CO", "FPLIM.CO", "FPMER.CO", "FPOPT.CO", "FPPAR.CO", "FPPEN.CO", "FPSAFE.CO", "FUISP.CO", "FYNBK.CO", "G4S.CO", "GABR.CO", "GEN.CO", "GERHSP-B.CO", "GES.CO", "GJ.CO", "GN.CO", "GRICLA.CO", "GRIEHY.CO", "GRINA.CO", "GRISEL.CO", "GRIUSHY.CO", "GRLA.CO", "GW.CO", "GYLD-A.CO", "GYLD-B.CO", "HAIDK.CO", "HAINOR.CO", "HAIVER.CO", "HARB-B.CO", "HART.CO", "HH.CO", "HOEJ-A.CO", "HOEJ-B.CO", "HVETBO.CO", "HVID.CO", "IC.CO", "IIIGLO.CO", "IIINEW.CO", "IMAIL-B.CO", "ISS.CO", "ISS-N.CO", "JDAN.CO", "JMI.CO", "JUTBK.CO", "JYIAKP.CO", "JYIDKA.CO", "JYIEUA.CO", "JYIFAV.CO", "JYIFJE.CO", "JYIGLO.CO", "JYIINA.CO", "JYIJAP.CO", "JYILAT.CO", "JYINOA.CO", "JYINYA.CO", "JYITYR.CO", "JYIUSA.CO", "JYSK.CO", "KBHL.CO", "KLEE-B.CO", "KRE.CO", "LASP.CO", "LAST-B.CO", "LL-A.CO", "LL-B.CO", "LOLB.CO", "LSIDK.CO", "LSIEU.CO", "LSILO.CO", "LSINA.CO", "LSIOBL.CO", "LSIVER.CO", "LUN.CO", "LUXOR-B.CO", "MAERSK-A.CO", "MAERSK-B.CO", "MAJAKT.CO", "MAJDKA.CO", "MAJDKO.CO", "MAJKON.CO", "MAJOBL.CO", "MAJPEN.CO", "MAJVAL.CO", "MATAS.CO", "MIGA-B.CO", "MNBA.CO", "MOLS.CO", "MT-B.CO", "NDA-DKK.CO", "NDIA.CO", "NDIAKTII.CO", "NDIDK.CO", "NDIDKA.CO", "NDIEM.CO", "NDIEU.CO", "NDIFJE.CO", "NDIGV.CO", "NDIHEA.CO", "NDIJAP.CO", "NDIUSA.CO", "NDIVER.CO", "NETOP.CO", "NEUR.CO", "NEWCAP.CO", "NGIVAL.CO", "NKT.CO", "NNIT.CO", "NORDIC.CO", "NORDJB.CO", "NORTHM.CO", "NOVO-B.CO", "NRDC.CO", "NRDF.CO", "NRSU.CO", "NTR-B.CO", "NUNA.CO", "NYIDA.CO", "NYIERO.CO", "NYIGLO.CO", "NYIKO.CO", "NYIKOB.CO", "NYILO.CO", "NYILOB.CO", "NZYM-B.CO", "OJBA.CO", "ONXEO.CO", "OSSR.CO", "OW.CO", "PARKEN.CO", "PNDORA.CO", "PRIMOF.CO", "PAAL-B.CO", "RBLN-B.CO", "RBREW.CO", "RELLA.CO", "RIAS-B.CO", "RILBA.CO", "ROCK-A.CO", "ROCK-B.CO", "ROV.CO", "RTX.CO", "SALB.CO", "SANI.CO", "SAS-DKK.CO", "SBS.CO", "SCD.CO", "SCHO.CO", "SEIDKA.CO", "SEIDAA.CO", "SEIEHU.CO", "SEIHYB.CO", "SEIIPK.CO", "SEIIPL.CO", "SEIIPM.CO", "SFG.CO", "SIF.CO", "SIM.CO", "SJGR.CO", "SKAKO.CO", "SKIAVK.CO", "SKIGLO.CO", "SKIKON.CO", "SKIVEK.CO", "SKJE.CO", "SMCAPDK.CO", "SOLAR-B.CO", "SPALOL.CO", "SPB.CO", "SPEAS.CO", "SPFA.CO", "SPG.CO", "SPICUM.CO", "SPIDJW.CO", "SPIDKA.CO", "SPIEUC.CO", "SPIEUG.CO", "SPIEUV.CO", "SPIJAG.CO", "SPIJAS.CO", "SPIJAV.CO", "SPIKOB.CO", "SPILOB.CO", "SPIMAK.CO", "SPIMAA.CO", "SPINOB.CO", "SPINOV.CO", "SPIUSG.CO", "SPIUSS.CO", "SPIUSV.CO", "SPIVA.CO", "SPIVAJ.CO", "SPIVUS.CO", "SPNO.CO", "SSILB.CO", "STRINV.CO", "STYLE.CO", "SVIESC.CO", "SYDB.CO", "SYIBRI.CO", "SYIBRIKAKK.CO", "SYIDK.CO", "SYIEU.CO", "SYIEUL.CO", "SYIFJE.CO", "SYIFJERNAKK.CO", "SYIIT.CO", "SYIKM.CO", "SYILAT.CO", "SYITYSKLAND.CO", "TDC.CO", "THRAN.CO", "TIV.CO", "TKDV.CO", "TNDR.CO", "TOP.CO", "TOPO.CO", "TORM-A.CO", "TOTA.CO", "TOWER.CO", "TPSL.CO", "TRIFOR.CO", "TRYG.CO", "UDV75.CO", "UIE.CO", "UPB.CO", "VAIBLUE.CO", "VAIGAK.CO", "VAIJAP.CO", "VEFY.CO", "VELO.CO", "VIBHK.CO", "VIINT.CO", "VIND.CO", "VIPRO.CO", "VJBA.CO", "VORD.CO", "VWS.CO", "WDH.CO", "ZEAL.CO", "AAB.CO"};
        //String [] symbols = new String[] {"STYLE.CO","DANTH.CO","NXEO.CO","IMAIL-B.CO","LUN.CO","CBRAIN.CO","ARKIL-B.CO","RRIA.CO","MIGA-B.CO","BIF.CO","VJBA.CO","PARKEN.CO","LOLB.CO","RTX.CO","TPSL.CO","MAERSK-A.CO","ELITE-B.CO","ISS.CO","G4S.CO","MAERSK-B.CO","CHEMM.CO","SKJE.CO","GN.CO","NRDF.CO","NORTHM.CO","ALMB.CO","NEWCAP.CO","SFG.CO","VELO.CO","SPEAS.CO","FED.CO","HH.CO","SSR.CO","NDA-DKK.CO","DKC.CO","NEUR.CO","SPG.CO","EGE-B.CO","RYG.CO","HARB-B.CO","AAB.CO","FFARMS.CO","JDAN.CO","SBS.CO","BO.CO","DAB.CO","GES.CO","TDC.CO","DNORD.CO","NOVO-B.CO","VIBHK.CO","VWS.CO","FLS.CO","KBHL.CO","UIE.CO","AOJ-P.CO","ROCK-A.CO","KAKO.CO","EXQ.CO","NORDIC.CO","TIV.CO","ROCK-B.CO","NZYM-B.CO","NIT.CO","SPNO.CO","LL-A.CO","EI.CO","TKDV.CO","SYDB.CO","BNORDIK-CSE.CO","NKT.CO","BLVIS.CO","AMBU-B.CO","GABR.CO","MOLS.CO","PRIMOF.CO","RIAS-B.CO","MNBA.CO","GRLA.CO","ZEAL.CO","RBREW.CO","FLUG-B.CO","HART.CO","DELTAQ.CO","BAVA.CO","SAS-DKK.CO","DSV.CO","LH.CO","KRE.CO","JUTBK.CO","CARL-B.CO","SALB.CO","SCHO.CO","JYSK.CO","TOP.CO","COLUM.CO","DANSKE.CO","RILBA.CO","TOTA.CO","RBLN-B.CO","WDH.CO","EXP-B.CO","ATAS.CO","BERLIV-B.CO","COLO-B.CO","AURI-B.CO","SOLAR-B.CO","GEN.CO","FYNBK.CO","CARL-A.CO","SANI.CO","DANT.CO","PNDORA.CO","MT-B.CO","HOEJ-A.CO","SIM.CO","PAAL-B.CO","CHR.CO","GJ.CO","HOEJ-B.CO","VIPRO.CO","FDS.CO","CPHNW.CO","GYLD-B.CO","ROV.CO","CPHCAP.CO","ALK-B.CO","VIINT.CO","BOCON-B.CO","IC.CO","BIOPOR.CO","OJBA.CO"};

        List<StockWrapper> stockWrappers = new ArrayList<StockWrapper>();

        stockWrappers.add(new StockWrapper("STYLE.CO"));
        stockWrappers.add(new StockWrapper("DANTH.CO"));
        stockWrappers.add(new StockWrapper("NXEO.CO"));
        stockWrappers.add(new StockWrapper("IMAIL-B.CO"));
        stockWrappers.add(new StockWrapper("LUN.CO"));
        stockWrappers.add(new StockWrapper("CBRAIN.CO"));
        stockWrappers.add(new StockWrapper("ARKIL-B.CO"));
        stockWrappers.add(new StockWrapper("RRIA.CO"));
        stockWrappers.add(new StockWrapper("MIGA-B.CO"));
        stockWrappers.add(new StockWrapper("BIF.CO"));
        stockWrappers.add(new StockWrapper("VJBA.CO"));
        stockWrappers.add(new StockWrapper("PARKEN.CO"));
        stockWrappers.add(new StockWrapper("LOLB.CO"));
        stockWrappers.add(new StockWrapper("RTX.CO"));
        stockWrappers.add(new StockWrapper("TPSL.CO"));
        stockWrappers.add(new StockWrapper("MAERSK-A.CO"));
        stockWrappers.add(new StockWrapper("ELITE-B.CO"));
        stockWrappers.add(new StockWrapper("ISS.CO"));
        stockWrappers.add(new StockWrapper("G4S.CO"));
        stockWrappers.add(new StockWrapper("MAERSK-B.CO"));
        stockWrappers.add(new StockWrapper("CHEMM.CO"));
        stockWrappers.add(new StockWrapper("SKJE.CO"));
        stockWrappers.add(new StockWrapper("GN.CO"));
        stockWrappers.add(new StockWrapper("NRDF.CO"));
        stockWrappers.add(new StockWrapper("NORTHM.CO"));
        stockWrappers.add(new StockWrapper("ALMB.CO"));
        stockWrappers.add(new StockWrapper("NEWCAP.CO"));
        stockWrappers.add(new StockWrapper("SFG.CO"));
        stockWrappers.add(new StockWrapper("VELO.CO"));
        stockWrappers.add(new StockWrapper("SPEAS.CO"));
        stockWrappers.add(new StockWrapper("FED.CO"));
        stockWrappers.add(new StockWrapper("HH.CO"));
        stockWrappers.add(new StockWrapper("SSR.CO"));
        stockWrappers.add(new StockWrapper("NDA-DKK.CO"));
        stockWrappers.add(new StockWrapper("DKC.CO"));
        stockWrappers.add(new StockWrapper("NEUR.CO"));
        stockWrappers.add(new StockWrapper("SPG.CO"));
        stockWrappers.add(new StockWrapper("EGE-B.CO"));
        stockWrappers.add(new StockWrapper("RYG.CO"));
        stockWrappers.add(new StockWrapper("HARB-B.CO"));
        stockWrappers.add(new StockWrapper("AAB.CO"));
        stockWrappers.add(new StockWrapper("FFARMS.CO"));
        stockWrappers.add(new StockWrapper("JDAN.CO"));
        stockWrappers.add(new StockWrapper("SBS.CO"));
        stockWrappers.add(new StockWrapper("BO.CO"));
        stockWrappers.add(new StockWrapper("DAB.CO"));
        stockWrappers.add(new StockWrapper("GES.CO"));
        stockWrappers.add(new StockWrapper("TDC.CO"));
        stockWrappers.add(new StockWrapper("DNORD.CO"));
        stockWrappers.add(new StockWrapper("NOVO-B.CO"));
        stockWrappers.add(new StockWrapper("VIBHK.CO"));
        stockWrappers.add(new StockWrapper("VWS.CO"));
        stockWrappers.add(new StockWrapper("FLS.CO"));
        stockWrappers.add(new StockWrapper("KBHL.CO"));
        stockWrappers.add(new StockWrapper("UIE.CO"));
        stockWrappers.add(new StockWrapper("AOJ-P.CO"));
        stockWrappers.add(new StockWrapper("ROCK-A.CO"));
        stockWrappers.add(new StockWrapper("KAKO.CO"));
        stockWrappers.add(new StockWrapper("EXQ.CO"));
        stockWrappers.add(new StockWrapper("NORDIC.CO"));
        stockWrappers.add(new StockWrapper("TIV.CO"));
        stockWrappers.add(new StockWrapper("ROCK-B.CO"));
        stockWrappers.add(new StockWrapper("NZYM-B.CO"));
        stockWrappers.add(new StockWrapper("NIT.CO"));
        stockWrappers.add(new StockWrapper("SPNO.CO"));
        stockWrappers.add(new StockWrapper("LL-A.CO"));
        stockWrappers.add(new StockWrapper("EI.CO"));
        stockWrappers.add(new StockWrapper("TKDV.CO"));
        stockWrappers.add(new StockWrapper("SYDB.CO"));
        stockWrappers.add(new StockWrapper("BNORDIK-CSE.CO"));
        stockWrappers.add(new StockWrapper("NKT.CO"));
        stockWrappers.add(new StockWrapper("BLVIS.CO"));
        stockWrappers.add(new StockWrapper("AMBU-B.CO"));
        stockWrappers.add(new StockWrapper("GABR.CO"));
        stockWrappers.add(new StockWrapper("MOLS.CO"));
        stockWrappers.add(new StockWrapper("PRIMOF.CO"));
        stockWrappers.add(new StockWrapper("RIAS-B.CO"));
        stockWrappers.add(new StockWrapper("MNBA.CO"));
        stockWrappers.add(new StockWrapper("GRLA.CO"));
        stockWrappers.add(new StockWrapper("ZEAL.CO"));
        stockWrappers.add(new StockWrapper("RBREW.CO"));
        stockWrappers.add(new StockWrapper("FLUG-B.CO"));
        stockWrappers.add(new StockWrapper("HART.CO"));
        stockWrappers.add(new StockWrapper("DELTAQ.CO"));
        stockWrappers.add(new StockWrapper("BAVA.CO"));
        stockWrappers.add(new StockWrapper("SAS-DKK.CO"));
        stockWrappers.add(new StockWrapper("DSV.CO"));
        stockWrappers.add(new StockWrapper("LH.CO"));
        stockWrappers.add(new StockWrapper("KRE.CO"));
        stockWrappers.add(new StockWrapper("JUTBK.CO"));
        stockWrappers.add(new StockWrapper("CARL-B.CO"));
        stockWrappers.add(new StockWrapper("SALB.CO"));
        stockWrappers.add(new StockWrapper("SCHO.CO"));
        stockWrappers.add(new StockWrapper("JYSK.CO"));
        stockWrappers.add(new StockWrapper("TOP.CO"));
        stockWrappers.add(new StockWrapper("COLUM.CO"));
        stockWrappers.add(new StockWrapper("DANSKE.CO"));
        stockWrappers.add(new StockWrapper("RILBA.CO"));
        stockWrappers.add(new StockWrapper("TOTA.CO"));
        stockWrappers.add(new StockWrapper("RBLN-B.CO"));
        stockWrappers.add(new StockWrapper("WDH.CO"));
        stockWrappers.add(new StockWrapper("EXP-B.CO"));
        stockWrappers.add(new StockWrapper("ATAS.CO"));
        stockWrappers.add(new StockWrapper("BERLIV-B.CO"));
        stockWrappers.add(new StockWrapper("COLO-B.CO"));
        stockWrappers.add(new StockWrapper("AURI-B.CO"));
        stockWrappers.add(new StockWrapper("SOLAR-B.CO"));
        stockWrappers.add(new StockWrapper("GEN.CO"));
        stockWrappers.add(new StockWrapper("FYNBK.CO"));
        stockWrappers.add(new StockWrapper("CARL-A.CO"));
        stockWrappers.add(new StockWrapper("SANI.CO"));
        stockWrappers.add(new StockWrapper("DANT.CO"));
        stockWrappers.add(new StockWrapper("PNDORA.CO"));
        stockWrappers.add(new StockWrapper("MT-B.CO"));
        stockWrappers.add(new StockWrapper("HOEJ-A.CO"));
        stockWrappers.add(new StockWrapper("SIM.CO"));
        stockWrappers.add(new StockWrapper("PAAL-B.CO"));
        stockWrappers.add(new StockWrapper("CHR.CO"));
        stockWrappers.add(new StockWrapper("GJ.CO"));
        stockWrappers.add(new StockWrapper("HOEJ-B.CO"));
        stockWrappers.add(new StockWrapper("VIPRO.CO"));
        stockWrappers.add(new StockWrapper("FDS.CO"));
        stockWrappers.add(new StockWrapper("CPHNW.CO"));
        stockWrappers.add(new StockWrapper("GYLD-B.CO"));
        stockWrappers.add(new StockWrapper("ROV.CO"));
        stockWrappers.add(new StockWrapper("CPHCAP.CO"));
        stockWrappers.add(new StockWrapper("ALK-B.CO"));
        stockWrappers.add(new StockWrapper("VIINT.CO"));
        stockWrappers.add(new StockWrapper("BOCON-B.CO"));
        stockWrappers.add(new StockWrapper("IC.CO"));
        stockWrappers.add(new StockWrapper("BIOPOR.CO"));
        stockWrappers.add(new StockWrapper("OJBA.CO"));



        TreeMap<Double, StockWrapper> momentumMap = new TreeMap<Double, StockWrapper>();
        for (StockWrapper stock : stockWrappers) {
            if(stock.stock != null) momentumMap.put(stock.getScore(), stock);
        }

        for (StockWrapper stock : momentumMap.values()) {

            System.out.println(stock);
        }
    }


}
