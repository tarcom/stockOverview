package dk.stockAnalyzer;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Persistens af kursdata i MariaDB — afløser den gamle Java-serialisering til
 * output/PersistedStocks.bin (PortefolioPersister).
 *
 * Genbruger 'stocks'-projektets MariaDB. Tabeller er prefixet stockOverview_:
 *   stockOverview_symbols (symbol, name, market_cap, updated_at)
 *   stockOverview_prices  (symbol, price_date, close)  PK(symbol, price_date)
 *
 * I modsætning til .bin-cachen akkumulerer DB'en historik over tid (UPSERT), så
 * vi har data at køre videre på efterfølgende.
 *
 * Credentials læses fra config/db.properties (gitignored), med override via env
 * STOCKOVERVIEW_DB_URL / _USER / _PASS.
 */
public class StockDb {

    private static String url;
    private static String user;
    private static String pass;
    private static boolean schemaEnsured = false;

    private static synchronized void loadConfig() {
        if (url != null) return;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream("config/db.properties")) {
            p.load(in);
        } catch (IOException e) {
            System.out.println("StockDb: kunne ikke læse config/db.properties (" + e.getMessage()
                    + ") — falder tilbage på env-variabler.");
        }
        url  = env("STOCKOVERVIEW_DB_URL",  p.getProperty("db.url"));
        user = env("STOCKOVERVIEW_DB_USER", p.getProperty("db.user"));
        pass = env("STOCKOVERVIEW_DB_PASS", p.getProperty("db.pass"));
        if (url == null || user == null) {
            throw new IllegalStateException("Mangler DB-konfiguration. Udfyld config/db.properties "
                    + "(se config/db.properties.example) eller sæt STOCKOVERVIEW_DB_URL/_USER/_PASS.");
        }
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }

    public static Connection getConnection() throws SQLException {
        loadConfig();
        return DriverManager.getConnection(url, user, pass);
    }

    /** Opretter alle ingestion-tabeller hvis de mangler. Kald én gang ved opstart. */
    public static synchronized void ensureSchema(Connection conn) throws SQLException {
        if (schemaEnsured) return;
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS stockOverview_securities ("
                    + "  symbol VARCHAR(40) NOT NULL, name VARCHAR(255) NULL,"
                    + "  exchange VARCHAR(64) NULL, full_exchange VARCHAR(128) NULL,"
                    + "  currency VARCHAR(16) NULL, financial_currency VARCHAR(16) NULL,"
                    + "  quote_type VARCHAR(32) NULL, sector VARCHAR(128) NULL,"
                    + "  industry VARCHAR(160) NULL, country VARCHAR(96) NULL,"
                    + "  employees INT NULL, website VARCHAR(255) NULL, business_summary TEXT NULL,"
                    + "  first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "  last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "  PRIMARY KEY (symbol), KEY idx_sector (sector), KEY idx_industry (industry),"
                    + "  KEY idx_country (country)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.execute("CREATE TABLE IF NOT EXISTS stockOverview_prices ("
                    + "  symbol VARCHAR(40) NOT NULL, price_date DATE NOT NULL,"
                    + "  open DOUBLE NULL, high DOUBLE NULL, low DOUBLE NULL, close DOUBLE NULL,"
                    + "  adj_close DOUBLE NULL, volume BIGINT NULL,"
                    + "  PRIMARY KEY (symbol, price_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.execute("CREATE TABLE IF NOT EXISTS stockOverview_dividends ("
                    + "  symbol VARCHAR(40) NOT NULL, ex_date DATE NOT NULL, amount DOUBLE NOT NULL,"
                    + "  PRIMARY KEY (symbol, ex_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.execute("CREATE TABLE IF NOT EXISTS stockOverview_splits ("
                    + "  symbol VARCHAR(40) NOT NULL, split_date DATE NOT NULL,"
                    + "  numerator DOUBLE NULL, denominator DOUBLE NULL,"
                    + "  PRIMARY KEY (symbol, split_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            st.execute("CREATE TABLE IF NOT EXISTS stockOverview_ingest_log ("
                    + "  symbol VARCHAR(40) NOT NULL, last_price_date DATE NULL,"
                    + "  last_fundamentals_date DATE NULL, price_points INT NULL,"
                    + "  status VARCHAR(32) NULL, error VARCHAR(512) NULL,"
                    + "  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "  PRIMARY KEY (symbol)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // Bred fundamentals-tabel (de vigtigste nøgletal som søgbare kolonner + rå JSON).
            st.execute("CREATE TABLE IF NOT EXISTS stockOverview_fundamentals ("
                    + "  symbol VARCHAR(40) NOT NULL, snapshot_date DATE NOT NULL,"
                    + "  current_price DOUBLE NULL, market_cap DOUBLE NULL, enterprise_value DOUBLE NULL,"
                    + "  trailing_pe DOUBLE NULL, forward_pe DOUBLE NULL, peg_ratio DOUBLE NULL,"
                    + "  price_to_book DOUBLE NULL, price_to_sales DOUBLE NULL, ev_to_revenue DOUBLE NULL,"
                    + "  ev_to_ebitda DOUBLE NULL, trailing_eps DOUBLE NULL, forward_eps DOUBLE NULL,"
                    + "  book_value DOUBLE NULL, profit_margins DOUBLE NULL, gross_margins DOUBLE NULL,"
                    + "  operating_margins DOUBLE NULL, ebitda_margins DOUBLE NULL, return_on_assets DOUBLE NULL,"
                    + "  return_on_equity DOUBLE NULL, revenue_growth DOUBLE NULL, earnings_growth DOUBLE NULL,"
                    + "  total_revenue DOUBLE NULL, ebitda DOUBLE NULL, gross_profits DOUBLE NULL,"
                    + "  free_cashflow DOUBLE NULL, operating_cashflow DOUBLE NULL, total_cash DOUBLE NULL,"
                    + "  total_debt DOUBLE NULL, debt_to_equity DOUBLE NULL, current_ratio DOUBLE NULL,"
                    + "  quick_ratio DOUBLE NULL, dividend_yield DOUBLE NULL, dividend_rate DOUBLE NULL,"
                    + "  payout_ratio DOUBLE NULL, beta DOUBLE NULL, shares_outstanding DOUBLE NULL,"
                    + "  float_shares DOUBLE NULL, held_pct_insiders DOUBLE NULL, held_pct_institutions DOUBLE NULL,"
                    + "  fifty_two_week_high DOUBLE NULL, fifty_two_week_low DOUBLE NULL, fifty_day_avg DOUBLE NULL,"
                    + "  two_hundred_day_avg DOUBLE NULL, fifty_two_week_change DOUBLE NULL,"
                    + "  recommendation_mean DOUBLE NULL, recommendation_key VARCHAR(24) NULL,"
                    + "  target_mean_price DOUBLE NULL, num_analyst_opinions INT NULL, raw_json LONGTEXT NULL,"
                    + "  PRIMARY KEY (symbol, snapshot_date), KEY idx_trailing_pe (trailing_pe),"
                    + "  KEY idx_roe (return_on_equity), KEY idx_market_cap (market_cap)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // Bevares til den gamle momentum-screener (uændret).
            st.execute("CREATE TABLE IF NOT EXISTS stockOverview_symbols ("
                    + "  symbol VARCHAR(40) NOT NULL, name VARCHAR(255) NULL,"
                    + "  market_cap DECIMAL(30,2) NULL,"
                    + "  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "  PRIMARY KEY (symbol)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        schemaEnsured = true;
    }

    // ================================================================
    //  Ingestion-API (bruges af Ingest; én Connection pr. worker-task)
    // ================================================================

    /** Symboler vi allerede ved ikke findes (status='not_found') — springes over. */
    public static Set<String> loadNotFound(Connection conn) throws SQLException {
        Set<String> set = new HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT symbol FROM stockOverview_ingest_log WHERE status='not_found'")) {
            while (rs.next()) set.add(rs.getString(1));
        }
        return set;
    }

    /** Symboler hentet OK inden for de seneste {@code windowDays} dage — springes over,
     *  så en afbrudt nat-backfill kan genoptages effektivt over FLERE nætter
     *  (rullende vindue, ikke kun "i dag", så genoptagelse på tværs af dage virker). */
    public static Set<String> loadRecentlyDone(Connection conn, int windowDays) throws SQLException {
        Set<String> set = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT symbol FROM stockOverview_ingest_log "
                + "WHERE status='ok' AND updated_at >= (NOW() - INTERVAL ? DAY)")) {
            ps.setInt(1, windowDays);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) set.add(rs.getString(1));
            }
        }
        return set;
    }

    /** Seneste lagrede kursdato for et symbol (til inkrementel hentning), eller null. */
    public static LocalDate getLastPriceDate(Connection conn, String symbol) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT MAX(price_date) FROM stockOverview_prices WHERE symbol=?")) {
            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date d = rs.getDate(1);
                    return d != null ? d.toLocalDate() : null;
                }
            }
        }
        return null;
    }

    /** UPSERT alle kurspunkter + udbytter + splits fra en DailyHistory. Returnerer antal kurspunkter. */
    public static int upsertPrices(Connection conn, String symbol, YahooClient.DailyHistory h) throws SQLException {
        String sql = "INSERT INTO stockOverview_prices "
                + "(symbol, price_date, open, high, low, close, adj_close, volume) "
                + "VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                + "open=VALUES(open), high=VALUES(high), low=VALUES(low), close=VALUES(close), "
                + "adj_close=VALUES(adj_close), volume=VALUES(volume)";
        int n = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (YahooClient.Bar b : h.bars) {
                ps.setString(1, symbol);
                ps.setDate(2, new Date(b.timestamp * 1000L));
                setD(ps, 3, b.open); setD(ps, 4, b.high); setD(ps, 5, b.low);
                setD(ps, 6, b.close); setD(ps, 7, b.adjClose);
                if (b.volume != null) ps.setLong(8, b.volume); else ps.setNull(8, Types.BIGINT);
                ps.addBatch();
                if (++n % 1000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
        if (!h.dividends.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO stockOverview_dividends (symbol, ex_date, amount) VALUES (?,?,?) "
                    + "ON DUPLICATE KEY UPDATE amount=VALUES(amount)")) {
                for (Map.Entry<Long, Double> e : h.dividends.entrySet()) {
                    ps.setString(1, symbol);
                    ps.setDate(2, new Date(e.getKey() * 1000L));
                    ps.setDouble(3, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        if (!h.splits.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO stockOverview_splits (symbol, split_date, numerator, denominator) "
                    + "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE numerator=VALUES(numerator), "
                    + "denominator=VALUES(denominator)")) {
                for (Map.Entry<Long, double[]> e : h.splits.entrySet()) {
                    ps.setString(1, symbol);
                    ps.setDate(2, new Date(e.getKey() * 1000L));
                    setD(ps, 3, Double.isNaN(e.getValue()[0]) ? null : e.getValue()[0]);
                    setD(ps, 4, Double.isNaN(e.getValue()[1]) ? null : e.getValue()[1]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
        return n;
    }

    /** UPSERT virksomheds-metadata + et fundamentals-snapshot fra quoteSummary-noden. */
    public static void upsertFundamentals(Connection conn, String symbol, LocalDate date, JsonNode s) throws SQLException {
        // --- securities (metadata) ---
        LinkedHashMap<String, Object> sec = new LinkedHashMap<>();
        sec.put("symbol", symbol);
        sec.put("name", coalesceStr(str(s, "price", "longName"), str(s, "price", "shortName")));
        sec.put("exchange", str(s, "price", "exchange"));
        sec.put("full_exchange", str(s, "price", "exchangeName"));
        sec.put("currency", str(s, "price", "currency"));
        sec.put("financial_currency", str(s, "financialData", "financialCurrency"));
        sec.put("quote_type", str(s, "price", "quoteType"));
        sec.put("sector", str(s, "summaryProfile", "sector"));
        sec.put("industry", str(s, "summaryProfile", "industry"));
        sec.put("country", str(s, "summaryProfile", "country"));
        sec.put("employees", intg(s, "summaryProfile", "fullTimeEmployees"));
        sec.put("website", str(s, "summaryProfile", "website"));
        sec.put("business_summary", str(s, "summaryProfile", "longBusinessSummary"));
        upsertDynamic(conn, "stockOverview_securities", sec, Collections.singleton("symbol"));

        // --- fundamentals (nøgletal-snapshot) ---
        LinkedHashMap<String, Object> f = new LinkedHashMap<>();
        f.put("symbol", symbol);
        f.put("snapshot_date", Date.valueOf(date));
        f.put("current_price", firstNonNull(dbl(s, "financialData", "currentPrice"), dbl(s, "price", "regularMarketPrice")));
        f.put("market_cap", firstNonNull(dbl(s, "price", "marketCap"), dbl(s, "summaryDetail", "marketCap")));
        f.put("enterprise_value", dbl(s, "defaultKeyStatistics", "enterpriseValue"));
        f.put("trailing_pe", firstNonNull(dbl(s, "summaryDetail", "trailingPE"), dbl(s, "price", "trailingPE")));
        f.put("forward_pe", firstNonNull(dbl(s, "summaryDetail", "forwardPE"), dbl(s, "defaultKeyStatistics", "forwardPE")));
        f.put("peg_ratio", firstNonNull(dbl(s, "defaultKeyStatistics", "pegRatio"), dbl(s, "defaultKeyStatistics", "trailingPegRatio")));
        f.put("price_to_book", dbl(s, "defaultKeyStatistics", "priceToBook"));
        f.put("price_to_sales", dbl(s, "summaryDetail", "priceToSalesTrailing12Months"));
        f.put("ev_to_revenue", dbl(s, "defaultKeyStatistics", "enterpriseToRevenue"));
        f.put("ev_to_ebitda", dbl(s, "defaultKeyStatistics", "enterpriseToEbitda"));
        f.put("trailing_eps", dbl(s, "defaultKeyStatistics", "trailingEps"));
        f.put("forward_eps", dbl(s, "defaultKeyStatistics", "forwardEps"));
        f.put("book_value", dbl(s, "defaultKeyStatistics", "bookValue"));
        f.put("profit_margins", firstNonNull(dbl(s, "financialData", "profitMargins"), dbl(s, "defaultKeyStatistics", "profitMargins")));
        f.put("gross_margins", dbl(s, "financialData", "grossMargins"));
        f.put("operating_margins", dbl(s, "financialData", "operatingMargins"));
        f.put("ebitda_margins", dbl(s, "financialData", "ebitdaMargins"));
        f.put("return_on_assets", dbl(s, "financialData", "returnOnAssets"));
        f.put("return_on_equity", dbl(s, "financialData", "returnOnEquity"));
        f.put("revenue_growth", dbl(s, "financialData", "revenueGrowth"));
        f.put("earnings_growth", dbl(s, "financialData", "earningsGrowth"));
        f.put("total_revenue", dbl(s, "financialData", "totalRevenue"));
        f.put("ebitda", dbl(s, "financialData", "ebitda"));
        f.put("gross_profits", dbl(s, "financialData", "grossProfits"));
        f.put("free_cashflow", dbl(s, "financialData", "freeCashflow"));
        f.put("operating_cashflow", dbl(s, "financialData", "operatingCashflow"));
        f.put("total_cash", dbl(s, "financialData", "totalCash"));
        f.put("total_debt", dbl(s, "financialData", "totalDebt"));
        f.put("debt_to_equity", dbl(s, "financialData", "debtToEquity"));
        f.put("current_ratio", dbl(s, "financialData", "currentRatio"));
        f.put("quick_ratio", dbl(s, "financialData", "quickRatio"));
        f.put("dividend_yield", firstNonNull(dbl(s, "summaryDetail", "dividendYield"), dbl(s, "summaryDetail", "trailingAnnualDividendYield")));
        f.put("dividend_rate", dbl(s, "summaryDetail", "dividendRate"));
        f.put("payout_ratio", dbl(s, "summaryDetail", "payoutRatio"));
        f.put("beta", firstNonNull(dbl(s, "summaryDetail", "beta"), dbl(s, "defaultKeyStatistics", "beta")));
        f.put("shares_outstanding", dbl(s, "defaultKeyStatistics", "sharesOutstanding"));
        f.put("float_shares", dbl(s, "defaultKeyStatistics", "floatShares"));
        f.put("held_pct_insiders", dbl(s, "defaultKeyStatistics", "heldPercentInsiders"));
        f.put("held_pct_institutions", dbl(s, "defaultKeyStatistics", "heldPercentInstitutions"));
        f.put("fifty_two_week_high", dbl(s, "summaryDetail", "fiftyTwoWeekHigh"));
        f.put("fifty_two_week_low", dbl(s, "summaryDetail", "fiftyTwoWeekLow"));
        f.put("fifty_day_avg", dbl(s, "summaryDetail", "fiftyDayAverage"));
        f.put("two_hundred_day_avg", dbl(s, "summaryDetail", "twoHundredDayAverage"));
        f.put("fifty_two_week_change", dbl(s, "defaultKeyStatistics", "52WeekChange"));
        f.put("recommendation_mean", dbl(s, "financialData", "recommendationMean"));
        f.put("recommendation_key", str(s, "financialData", "recommendationKey"));
        f.put("target_mean_price", dbl(s, "financialData", "targetMeanPrice"));
        f.put("num_analyst_opinions", intg(s, "financialData", "numberOfAnalystOpinions"));
        f.put("raw_json", s.toString());
        upsertDynamic(conn, "stockOverview_fundamentals", f,
                new HashSet<>(Arrays.asList("symbol", "snapshot_date")));
    }

    /** Skriver/erstatter ingest-log-rækken for et symbol. */
    public static void logIngest(Connection conn, String symbol, LocalDate lastPrice,
                                 LocalDate lastFund, Integer points, String status, String error) throws SQLException {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", symbol);
        m.put("last_price_date", lastPrice != null ? Date.valueOf(lastPrice) : null);
        m.put("last_fundamentals_date", lastFund != null ? Date.valueOf(lastFund) : null);
        m.put("price_points", points);
        m.put("status", status);
        m.put("error", error != null && error.length() > 500 ? error.substring(0, 500) : error);
        upsertDynamic(conn, "stockOverview_ingest_log", m, Collections.singleton("symbol"));
    }

    // ------------------------------------------------------------- helpers

    /** Generisk UPSERT: bygger INSERT … ON DUPLICATE KEY UPDATE ud fra et kolonne→værdi-map. */
    private static void upsertDynamic(Connection conn, String table,
                                      LinkedHashMap<String, Object> cols, Set<String> keyCols) throws SQLException {
        String colList = String.join(",", cols.keySet());
        String placeholders = cols.keySet().stream().map(k -> "?").collect(Collectors.joining(","));
        String updates = cols.keySet().stream()
                .filter(k -> !keyCols.contains(k))
                .map(k -> k + "=VALUES(" + k + ")")
                .collect(Collectors.joining(","));
        String sql = "INSERT INTO " + table + " (" + colList + ") VALUES (" + placeholders + ")"
                + (updates.isEmpty() ? "" : " ON DUPLICATE KEY UPDATE " + updates);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Object v : cols.values()) {
                if (v == null) ps.setObject(i, null);
                else ps.setObject(i, v);
                i++;
            }
            ps.executeUpdate();
        }
    }

    private static void setD(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v != null && !v.isNaN() && !v.isInfinite()) ps.setDouble(idx, v);
        else ps.setNull(idx, Types.DOUBLE);
    }

    private static Double dbl(JsonNode root, String module, String field) {
        JsonNode n = root.path(module).path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        if (n.has("raw")) { JsonNode r = n.get("raw"); return r.isNumber() ? r.asDouble() : null; }
        return n.isNumber() ? n.asDouble() : null;
    }

    private static Integer intg(JsonNode root, String module, String field) {
        Double d = dbl(root, module, field);
        return d == null ? null : (int) Math.round(d);
    }

    private static String str(JsonNode root, String module, String field) {
        JsonNode n = root.path(module).path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        if (n.isTextual()) return n.asText();
        if (n.has("fmt") && n.get("fmt").isTextual()) return n.get("fmt").asText();
        return null;
    }

    private static Double firstNonNull(Double a, Double b) { return a != null ? a : b; }
    private static String coalesceStr(String a, String b) { return a != null ? a : b; }

    // ------------------------------------------------------------------ persist

    /** Skriver (UPSERT) symboler + alle kurspunkter for de hentede aktier. */
    public static void persist(List<StockWrapper> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            System.out.println("StockDb.persist: ingen aktier at gemme.");
            return;
        }
        String upsertSymbol = "INSERT INTO stockOverview_symbols (symbol, name, market_cap) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE "
                + "name = VALUES(name), market_cap = VALUES(market_cap)";
        String upsertPrice = "INSERT INTO stockOverview_prices (symbol, price_date, close) "
                + "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE close = VALUES(close)";

        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            conn.setAutoCommit(false);
            int symRows = 0, priceRows = 0;

            try (PreparedStatement ps = conn.prepareStatement(upsertSymbol)) {
                for (StockWrapper sw : stocks) {
                    ps.setString(1, sw.getSymbol());
                    ps.setString(2, sw.getName());
                    if (sw.getMarketCap() != null) ps.setBigDecimal(3, sw.getMarketCap());
                    else ps.setNull(3, Types.DECIMAL);
                    ps.addBatch();
                    symRows++;
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = conn.prepareStatement(upsertPrice)) {
                int batched = 0;
                for (StockWrapper sw : stocks) {
                    HashMap<Integer, Double> closes = sw.getHistoricalValues();
                    HashMap<Integer, Calendar> dates = sw.getHistoricalValues2();
                    if (closes == null || dates == null) continue;
                    for (Map.Entry<Integer, Double> e : closes.entrySet()) {
                        Calendar cal = dates.get(e.getKey());
                        if (cal == null || e.getValue() == null) continue;
                        ps.setString(1, sw.getSymbol());
                        ps.setDate(2, new java.sql.Date(cal.getTimeInMillis()));
                        ps.setDouble(3, e.getValue());
                        ps.addBatch();
                        priceRows++;
                        if (++batched % 1000 == 0) ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }

            conn.commit();
            System.out.printf("StockDb.persist: gemte %d symboler og %d kurspunkter.%n", symRows, priceRows);
        } catch (SQLException e) {
            System.out.println("StockDb.persist FEJLEDE: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // --------------------------------------------------------------------- load

    /**
     * Læser kursdata tilbage fra DB og genopbygger StockWrapper-listen (index 0 =
     * nyeste, som AllansStrategy forventer). Bruger samme ≥70%-historik-tærskel som
     * det live fetch. limit = -1 for alle symboler.
     */
    public static List<StockWrapper> load(int daysHistory, int limit) {
        int needed   = daysHistory + 2;
        int minClose = (int) Math.ceil(needed * YahooStockFetcher.MIN_HISTORY_FRACTION);

        // Hent navn + market cap
        Map<String, String>      names = new HashMap<>();
        Map<String, BigDecimal>  caps  = new HashMap<>();
        List<StockWrapper> result = new ArrayList<>();

        try (Connection conn = getConnection()) {
            ensureSchema(conn);

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT symbol, name, market_cap FROM stockOverview_symbols")) {
                while (rs.next()) {
                    names.put(rs.getString("symbol"), rs.getString("name"));
                    caps.put(rs.getString("symbol"), rs.getBigDecimal("market_cap"));
                }
            }

            // Alle kurser, nyeste først pr. symbol — grupperes i Java.
            String currentSymbol = null;
            HashMap<Integer, Double> closeMap = null;
            HashMap<Integer, Calendar> dateMap = null;
            int idx = 0;
            int symbolsAdded = 0;

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT symbol, price_date, close FROM stockOverview_prices "
                         + "ORDER BY symbol, price_date DESC")) {
                while (rs.next()) {
                    String sym = rs.getString("symbol");
                    if (!sym.equals(currentSymbol)) {
                        // luk forrige symbol
                        if (currentSymbol != null) {
                            addIfEnough(result, currentSymbol, closeMap, dateMap, minClose,
                                    names, caps);
                            if (limit > 0 && result.size() >= limit) { currentSymbol = null; break; }
                        }
                        currentSymbol = sym;
                        closeMap = new HashMap<>();
                        dateMap  = new HashMap<>();
                        idx = 0;
                    }
                    if (idx < needed) {
                        closeMap.put(idx, rs.getDouble("close"));
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(rs.getDate("price_date").getTime());
                        dateMap.put(idx, cal);
                        idx++;
                    }
                }
                // sidste symbol
                if (currentSymbol != null && !(limit > 0 && result.size() >= limit)) {
                    addIfEnough(result, currentSymbol, closeMap, dateMap, minClose, names, caps);
                }
            }
        } catch (SQLException e) {
            System.out.println("StockDb.load FEJLEDE: " + e.getMessage());
            throw new RuntimeException(e);
        }

        System.out.printf("StockDb.load: indlæste %d aktier fra DB (min %d/%d kurspunkter).%n",
                result.size(), minClose, needed);
        return result;
    }

    private static void addIfEnough(List<StockWrapper> result, String symbol,
                                    HashMap<Integer, Double> closeMap, HashMap<Integer, Calendar> dateMap,
                                    int minClose, Map<String, String> names, Map<String, BigDecimal> caps) {
        if (closeMap.size() < minClose) return;
        String name = names.getOrDefault(symbol, symbol);
        StockWrapper sw = new StockWrapper(name, symbol, closeMap, dateMap, caps.get(symbol));
        result.add(sw);
    }
}
