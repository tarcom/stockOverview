package dk.stockAnalyzer;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Data-ingestion til screener-databasen. For hvert symbol:
 *   1) kurser via v8/chart  — fuld backfill af HELE historikken (range=max) første gang,
 *      ellers kun nye dage (inkrementel ud fra MAX(price_date) i DB). INGEST_BACKFILL_ALL=1
 *      tvinger fuld gen-hentning af alle → stockOverview_prices/_dividends/_splits
 *   2) fundamentals via v10/quoteSummary → stockOverview_securities + _fundamentals (snapshot)
 *   3) status i stockOverview_ingest_log (resumerbar; status='not_found' OG symboler
 *      hentet OK inden for RESUME_WINDOW_DAYS springes over — så en afbrudt nat-backfill
 *      genoptages effektivt over FLERE nætter). INGEST_FORCE=1 (env) gen-henter alt uanset.
 *
 * ALLE tickers ingestes uanset historik-længde — screeneren filtrerer selv bagefter.
 *
 * Kørsel:
 *   mvn exec:java -Dexec.mainClass=dk.stockAnalyzer.Ingest                  (alle symboler)
 *   mvn exec:java -Dexec.mainClass=dk.stockAnalyzer.Ingest -Dexec.args="200" (test: kun 200)
 *   java -jar target/app.jar 200
 * args[0] = limit (intet/<=0 = alle).
 */
public class Ingest {

    /** Genoptagelses-vindue: symboler hentet OK inden for så mange dage springes over,
     *  så en backfill kan strække sig over flere nætter uden at re-hente. */
    private static final int RESUME_WINDOW_DAYS = 7;

    private static final AtomicInteger done      = new AtomicInteger(0);
    private static final AtomicInteger okCount    = new AtomicInteger(0);
    private static final AtomicInteger notFound   = new AtomicInteger(0);
    private static final AtomicInteger errCount   = new AtomicInteger(0);
    private static final AtomicInteger fullBackfill= new AtomicInteger(0);
    private static volatile int total = 0;
    private static volatile long startTime = 0;

    /** INGEST_BACKFILL_ALL=1: tving fuld gen-hentning af HELE historikken (range=max) for
     *  alle symboler — også dem der allerede har data. Brug det én gang til at hente
     *  historik længere tilbage end de 10 år der tidligere blev gemt. Implicerer FORCE. */
    private static final boolean BACKFILL_ALL = "1".equals(System.getenv("INGEST_BACKFILL_ALL"));

    public static void main(String[] args) throws Exception {
        int limit = -1;
        if (args.length > 0) {
            try { limit = Integer.parseInt(args[0].trim()); }
            catch (NumberFormatException e) { System.out.println("Ugyldig limit '" + args[0] + "', kører alle."); }
        }
        System.out.println("=== Ingest start (limit=" + (limit > 0 ? limit : "ALLE") + ") ===");

        // Schema + skip-liste. Default springer vi 'not_found' + symboler hentet OK inden for
        // RESUME_WINDOW_DAYS over — så en afbrudt nat-backfill kan genoptages over flere
        // nætter (kør bare igen; den fortsætter hvor den slap). INGEST_FORCE=1 gen-henter alt
        // (brug det til den daglige inkrementelle kørsel, hvor alle tickers skal opdateres).
        boolean force = "1".equals(System.getenv("INGEST_FORCE"));
        Set<String> skip;
        try (Connection conn = StockDb.getConnection()) {
            StockDb.ensureSchema(conn);
            skip = StockDb.loadNotFound(conn);
            int nf = skip.size();
            if (BACKFILL_ALL) {
                // Genoptag-bart: springer kun 'not_found' + symboler der ALLEREDE er
                // backfillet (status='backfilled'). Stop og kør igen → fortsætter hvor
                // den slap. (Pausér gerne den daglige cron under kampagnen, så den ikke
                // overskriver 'backfilled' → 'ok'.)
                Set<String> done = StockDb.loadBackfilled(conn);
                skip.addAll(done);
                System.out.println("INGEST_BACKFILL_ALL=1: fuld gen-hentning (range=max). Springer "
                        + nf + " 'not_found' + " + done.size() + " allerede-backfillede over (genoptager).");
            } else if (force) {
                System.out.println("INGEST_FORCE=1: springer kun " + nf + " 'not_found' over (gen-henter alt).");
            } else {
                Set<String> recent = StockDb.loadRecentlyDone(conn, RESUME_WINDOW_DAYS);
                skip.addAll(recent);
                System.out.println("Springer " + nf + " 'not_found' + " + recent.size()
                        + " hentet inden for " + RESUME_WINDOW_DAYS + " dage over.");
            }
        }

        // Symboler
        List<String> symbols = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("doc" + File.separator + "allYahooStocks.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || skip.contains(line)) continue;
                symbols.add(line);
                if (limit > 0 && symbols.size() >= limit) break;
            }
        }
        total = symbols.size();
        startTime = System.currentTimeMillis();
        System.out.println("Ingester " + total + " symboler med " + YahooClient.parallelism + " tråde...");

        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ingest-progress"); t.setDaemon(true); return t;
        });
        reporter.scheduleAtFixedRate(() -> printProgress(false), 15, 15, TimeUnit.SECONDS);

        ExecutorService pool = Executors.newFixedThreadPool(YahooClient.parallelism);
        List<Future<?>> futures = new ArrayList<>();
        for (String sym : symbols) futures.add(pool.submit(() -> ingestOne(sym)));
        pool.shutdown();
        for (Future<?> f : futures) { try { f.get(); } catch (Exception ignored) {} }

        reporter.shutdownNow();
        printProgress(true);
    }

    private static void ingestOne(String symbol) {
        try (Connection conn = StockDb.getConnection()) {
            LocalDate last = StockDb.getLastPriceDate(conn, symbol);
            Long period1 = null; // null => fuld backfill (range=max)
            if (last != null && !BACKFILL_ALL) {
                // re-hent et lille overlap for at fange korrektioner
                period1 = last.minusDays(4).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            } else {
                fullBackfill.incrementAndGet();
            }

            YahooClient.DailyHistory hist = YahooClient.getDailyHistory(symbol, period1);
            int points = StockDb.upsertPrices(conn, symbol, hist);

            LocalDate lastPrice = hist.bars.isEmpty() ? last
                    : LocalDate.ofEpochDay(hist.bars.get(hist.bars.size() - 1).timestamp / 86400L);

            // Fundamentals-snapshot (dagligt). Fejl her må ikke vælte kurs-ingestion.
            LocalDate fundDate = null;
            try {
                JsonNode summary = YahooClient.getQuoteSummary(symbol);
                if (summary != null) {
                    fundDate = LocalDate.now();
                    StockDb.upsertFundamentals(conn, symbol, fundDate, summary);
                }
            } catch (Exception fe) {
                // fundamentals kan fejle (fx fonde/indeks uden nøgletal) — kurser er stadig gemt
            }

            // I backfill-kampagnen markeres som 'backfilled' så genstart kan springe dem over.
            StockDb.logIngest(conn, symbol, lastPrice, fundDate, points, BACKFILL_ALL ? "backfilled" : "ok", null);
            okCount.incrementAndGet();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            try (Connection conn = StockDb.getConnection()) {
                if (msg.contains("HTTP 404")) {
                    StockDb.logIngest(conn, symbol, null, null, null, "not_found", msg);
                    notFound.incrementAndGet();
                } else {
                    StockDb.logIngest(conn, symbol, null, null, null, "error", msg);
                    errCount.incrementAndGet();
                }
            } catch (Exception ignored) {
                errCount.incrementAndGet();
            }
        } finally {
            done.incrementAndGet();
        }
    }

    private static void printProgress(boolean isFinal) {
        int d = done.get();
        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
        double perMin = elapsedSec > 0 ? (d * 60.0 / elapsedSec) : 0;
        String elapsed = String.format("%dh %02dm %02ds", elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60);
        String eta = "";
        if (!isFinal && perMin > 0 && d < total) {
            long etaSec = (long) ((total - d) * 60.0 / perMin);
            eta = String.format("  ETA: %dh %02dm", etaSec / 3600, (etaSec % 3600) / 60);
        }
        if (isFinal) {
            System.out.println("\n=== INGEST AFSLUTTET ===");
            System.out.printf("Tid: %s   Behandlet: %d/%d%n", elapsed, d, total);
            System.out.printf("OK: %d  (heraf fuld backfill: %d)%n", okCount.get(), fullBackfill.get());
            System.out.printf("Not found: %d   Fejl: %d%n", notFound.get(), errCount.get());
            System.out.printf("Hastighed: %.0f symboler/min  (sluttede på %dms/slot)%n", perMin, YahooClient.currentDelayMs);
            System.out.println("========================");
        } else {
            System.out.printf("[%s]  %d/%d (%.1f%%)  ok=%d nf=%d err=%d  %.0f/min  [%dms/slot]%s%n",
                    elapsed, d, total, total > 0 ? d * 100.0 / total : 0,
                    okCount.get(), notFound.get(), errCount.get(), perMin, YahooClient.currentDelayMs, eta);
        }
    }
}
