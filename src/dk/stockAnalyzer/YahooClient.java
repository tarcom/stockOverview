package dk.stockAnalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Selvstændig Yahoo Finance-klient til 2026.
 *
 * Yahoo kræver siden 2023 en cookie + crumb-token på sine API-kald. Det gamle
 * yahoofinance-api 3.17.0 håndterer ikke det flow (rammer v7/quote uden crumb -> 401),
 * så denne klasse laver handshaket selv:
 *   1) GET https://fc.yahoo.com              -> sætter A-serie cookie
 *   2) GET .../v1/test/getcrumb (med cookie) -> crumb-token
 *   3) v8/chart (historik) og v7/quote (navn + marketCap) kaldes med cookie (+crumb)
 *
 * VIGTIGT (2026-fund): getcrumb afviser en detaljeret browser-User-Agent (fuld
 * Chrome-streng) med HTTP 429, men accepterer den generiske "Mozilla/5.0". Brug
 * derfor IKKE en realistisk Chrome-UA her — det udløser bot-beskyttelsen.
 *
 * Indeholder rate limiting og 429-backoff (CLAUDE.md #4) så vi ikke ryger i
 * Yahoos penalty-box ved tusindvis af kald. Bemærk: getcrumb har en meget stram
 * rate-limit — den kaldes kun én gang pr. kørsel (caches).
 */
public class YahooClient {

    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String COOKIE_URL = "https://fc.yahoo.com";
    private static final String CRUMB_URL  = "https://query1.finance.yahoo.com/v1/test/getcrumb";
    private static final String CHART_URL  = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String QUOTE_URL  = "https://query1.finance.yahoo.com/v7/finance/quote";

    /** Antal parallelle tråde. */
    public static int parallelism = 8;
    private static final int MAX_RETRIES = 3;

    // --- Adaptiv rate limiting (AIMD) ---
    private static final long MIN_DELAY_MS = 80;
    private static final long MAX_DELAY_MS = 4000;
    private static final int  SUCCESSES_BEFORE_SPEEDUP = 15;
    public  static volatile long currentDelayMs = 120;
    private static final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String cookie;
    private static String crumb;
    private static long lastRequestTime = 0;

    // ---------------------------------------------------------------- auth

    private static synchronized void ensureAuth() throws IOException {
        if (cookie != null && crumb != null) {
            return;
        }
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            throttle();
            try {
                cookie = fetchCookie();
                crumb = fetchCrumb(cookie);
                System.out.println("YahooClient: hentede cookie + crumb (crumb=" + crumb + ")");
                return;
            } catch (IOException e) {
                cookie = null;
                crumb = null;
                last = e;
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    long backoff = 2000L * (1L << attempt); // 2s, 4s, 8s, 16s
                    System.out.println("Auth-handshake fik 429, backoff " + backoff
                            + "ms (forsøg " + (attempt + 1) + "/" + (MAX_RETRIES + 1) + ")");
                    sleep(backoff);
                    continue;
                }
                throw e; // andre fejl giver ikke mening at gentage
            }
        }
        throw last != null ? last : new IOException("Auth-handshake fejlede");
    }

    private static String fetchCookie() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(COOKIE_URL).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        // Følg IKKE redirects: Set-Cookie sættes på fc.yahoo.com's direkte svar og går
        // tabt hvis vi følger videre (getHeaderFields() viser kun sidste hops headers).
        conn.setInstanceFollowRedirects(false);
        try {
            conn.getResponseCode(); // fc.yahoo.com svarer typisk 404 men sætter cookie
        } catch (IOException ignored) {
            // selv ved fejl kan Set-Cookie være sat
        }
        StringBuilder sb = new StringBuilder();
        Map<String, List<String>> headers = conn.getHeaderFields();
        List<String> setCookies = headers.get("Set-Cookie");
        if (setCookies == null) {
            setCookies = headers.get("set-cookie");
        }
        if (setCookies != null) {
            for (String sc : setCookies) {
                String pair = sc.split(";", 2)[0];
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(pair);
            }
        }
        conn.disconnect();
        if (sb.length() == 0) {
            throw new IOException("Kunne ikke hente Yahoo-cookie fra " + COOKIE_URL);
        }
        return sb.toString();
    }

    private static String fetchCrumb(String cookie) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(CRUMB_URL).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Cookie", cookie);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new IOException("Kunne ikke hente crumb, HTTP " + code);
        }
        String result = new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8).trim();
        conn.disconnect();
        if (result.isEmpty()) {
            throw new IOException("Tom crumb fra Yahoo");
        }
        return result;
    }

    // ------------------------------------------- HTTP m. adaptiv rate + backoff

    private static synchronized void throttle() {
        long wait = currentDelayMs - (System.currentTimeMillis() - lastRequestTime);
        if (wait > 0) sleep(wait);
        lastRequestTime = System.currentTimeMillis();
    }

    private static synchronized void onSuccess() {
        int s = consecutiveSuccesses.incrementAndGet();
        if (s >= SUCCESSES_BEFORE_SPEEDUP) {
            consecutiveSuccesses.set(0);
            long prev = currentDelayMs;
            currentDelayMs = Math.max(MIN_DELAY_MS, (long)(currentDelayMs * 0.85));
            if (currentDelayMs < prev) {
                System.out.printf("  [rate] ↑ → %dms/slot%n", currentDelayMs);
            }
        }
    }

    private static synchronized void onRateLimitHit() {
        consecutiveSuccesses.set(0);
        long prev = currentDelayMs;
        currentDelayMs = Math.min(MAX_DELAY_MS, currentDelayMs * 2);
        System.out.printf("  [rate] ↓ 429: %dms → %dms/slot%n", prev, currentDelayMs);
    }

    private static JsonNode getJson(String urlStr) throws IOException {
        ensureAuth();
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            throttle();
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Cookie", cookie);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream()) {
                    JsonNode result = MAPPER.readTree(in);
                    onSuccess();
                    return result;
                } finally {
                    conn.disconnect();
                }
            }
            conn.disconnect();
            if (code == 429) {
                onRateLimitHit();
                long backoff = 2000L * (1L << attempt);
                sleep(backoff);
                last = new IOException("HTTP 429 for " + urlStr);
                continue;
            }
            if (code == 401) {
                cookie = null;
                crumb = null;
                ensureAuth();
                last = new IOException("HTTP 401 for " + urlStr);
                continue;
            }
            throw new IOException("HTTP " + code + " for " + urlStr);
        }
        throw last != null ? last : new IOException("getJson fejlede: " + urlStr);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    // ----------------------------------------------------------- data-API

    /** Daglige luk-priser, ældste -> nyeste. */
    public static class History {
        public final List<Double> closes = new ArrayList<>();
        public final List<Long> timestamps = new ArrayList<>(); // epoch-sekunder
    }

    /** Henter daglig historik via v8/chart. calendarDays = antal kalenderdage tilbage. */
    public static History getHistory(String symbol, int calendarDays) throws IOException {
        long now = System.currentTimeMillis() / 1000L;
        long from = now - (long) calendarDays * 24 * 3600;
        String url = CHART_URL + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "?period1=" + from + "&period2=" + now + "&interval=1d";
        JsonNode root = getJson(url);
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.size() == 0) {
            throw new IOException("Ingen chart-result for " + symbol);
        }
        JsonNode r0 = result.get(0);
        JsonNode ts = r0.path("timestamp");
        JsonNode quoteArr = r0.path("indicators").path("quote");
        History h = new History();
        if (ts.isArray() && quoteArr.isArray() && quoteArr.size() > 0) {
            JsonNode closeArr = quoteArr.get(0).path("close");
            for (int i = 0; i < ts.size() && i < closeArr.size(); i++) {
                JsonNode c = closeArr.get(i);
                if (c == null || c.isNull()) {
                    continue; // spring huller over (helligdage/manglende data)
                }
                h.timestamps.add(ts.get(i).asLong());
                h.closes.add(c.asDouble());
            }
        }
        return h;
    }

    /** Navn + market cap. */
    public static class Quote {
        public String name;
        public BigDecimal marketCap;
    }

    /** Henter navn + market cap for op til 100 symboler i ét kald. */
    public static Map<String, Quote> getQuoteBatch(List<String> symbols) throws IOException {
        ensureAuth();
        Map<String, Quote> result = new HashMap<>();
        int batchSize = 100;
        for (int i = 0; i < symbols.size(); i += batchSize) {
            List<String> batch = symbols.subList(i, Math.min(i + batchSize, symbols.size()));
            String symbolsParam = String.join(",", batch);
            String url = QUOTE_URL + "?symbols=" + URLEncoder.encode(symbolsParam, StandardCharsets.UTF_8)
                    + "&crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);
            try {
                JsonNode arr = getJson(url).path("quoteResponse").path("result");
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        String sym = n.path("symbol").asText(null);
                        if (sym == null) continue;
                        Quote q = new Quote();
                        if (n.hasNonNull("longName"))        q.name = n.get("longName").asText();
                        else if (n.hasNonNull("shortName"))  q.name = n.get("shortName").asText();
                        else if (n.hasNonNull("displayName"))q.name = n.get("displayName").asText();
                        if (n.hasNonNull("marketCap"))
                            q.marketCap = new BigDecimal(n.get("marketCap").asText());
                        result.put(sym, q);
                    }
                }
            } catch (Exception e) {
                System.out.println("Batch-quote fejl (ignoreret): " + e.getMessage());
            }
        }
        return result;
    }
}
