# StockOverview — Projekt-kontekst (2026)

## Hvad er dette?

Et aktie-screener-system i **tre dele**:

1. **Ingestion (Java, `dk.stockAnalyzer.Ingest`)** — henter ALT tilgængeligt data for hver
   ticker fra Yahoo Finance og lægger det i MariaDB: **hele den daglige OHLCV-historik**
   (op til ~60+ år), udbytter/splits, virksomheds-metadata (sektor/industri/land m.m.) og
   ~45 fundamentale nøgletal. Fundamentet man screener oven på.
2. **Screener-web-portal (PHP+MySQL, `screener/`)** — det centrale produkt: en webportal
   ("Nørgaard's Aktie Screener") med filtre, base-100-grafer, teknisk analyse (SMA/RSI/MACD/
   volumen), presets, gemte screens og favoritter. Den fuldscanner en bred, **forudberegnet**
   tabel (`stockOverview_screener`) på millisekunder. Bygges af `screener/bin/precompute.php`.
   **Se `screener/CLAUDE.md`-noterne i auto-memory for detaljer.** Kører på HTPC, portabel til
   aogj.com (one.com) senere. Datakilden holdes hemmelig — **nævn aldrig Yahoo i UI-tekst.**
3. **Momentum-screener (gammel, `dk.stockAnalyzer.Main`)** — den oprindelige 2015-kode:
   "Allan's Strategy" (momentum-scoring) + Excel/links-export. Uændret; kører nu på DB-data.

**Daglig drift:** cron kører `daily_update.sh` kl. 04:00 (inkrementel ingest + precompute).
Projektet er skrevet i 2015–2017, moderniseret 2026 (Maven, live Yahoo-data, crumb/cookie,
MariaDB, PHP-screener). Validér end-to-end ved at køre `Ingest` med en lille limit.

---

## Ingestion — `dk.stockAnalyzer.Ingest` (primær path)

```
allYahooStocks.txt (~150k symboler)
      ↓  (pr. symbol, 8 tråde)
YahooClient.getDailyHistory   v8/chart  →  HELE OHLCV-historikken + adjclose + udbytter/splits
YahooClient.getQuoteSummary   v10/quoteSummary  →  profil + ~45 nøgletal
      ↓
StockDb (UPSERT)  →  stockOverview_{prices,dividends,splits,securities,fundamentals,ingest_log}
```

**Inkrementel som standard:** for hvert symbol slås `MAX(price_date)` op. Tom → **fuld
backfill af hele historikken** (`period1=0&period2=now&interval=1d`). Ellers hentes kun nye
dage (`period1 = sidste dato − 4 dages overlap`). `INSERT … ON DUPLICATE KEY UPDATE` overalt
(idempotent). **Weekend-bjælker droppes i parseren** (børser handler ikke lør/søn).

**Dyb historik-backfill (`INGEST_BACKFILL_ALL=1`):** tvinger fuld gen-hentning (period1=0) af
ALLE symboler, også dem der allerede har data — bruges én gang til at hente historik dybere
end de tidligere 10 år. Genoptag-bart: færdige symboler logges `status='backfilled'` og
springes over ved genstart. Pausér den daglige cron under en sådan kampagne.

**Gotcha (vigtig):** den oprindelige `range=max` returnerede MÅNEDLIGE bjælker (timestampet d.
1. i hver mdr, med måneds-volumen, endda på weekender) blandet ind i de daglige → fantom-
spikes i volumen + kurs. Derfor bruges nu `period1=0&interval=1d` + weekend-skip. Skulle de
opstå igen efter en backfill: `DELETE FROM stockOverview_prices WHERE DAYOFMONTH(price_date)=1`
(slet pr. symbol for at undgå én kæmpe-transaktion) + kør precompute igen.

**Alle tickers ingestes** uanset historik-længde — 70%-grænsen hører til *screening*, ikke
ingestion. Symboler der giver HTTP 404 logges som `status='not_found'` i `ingest_log` og
springes over. Symboler **hentet OK inden for `RESUME_WINDOW_DAYS` (7)** springes også over,
så en afbrudt nat-backfill genoptages effektivt over flere nætter (kør bare `mvn exec:java`
igen næste nat — den fortsætter hvor den slap). Den daglige inkrementelle kørsel skal
derimod opdatere ALLE tickers → kør den med `INGEST_FORCE=1` (ignorerer resume-skip).

**Tidsforbrug:** ~118 symboler/min i korte tests, men en fuld 150k-kørsel rammer Yahoos
time/døgn-throttling → realistisk **2-3 nætter** (best case ~21t). Kør i `screen`/`nohup`;
den er resumerbar pr. nat.

**CLI — limit som første arg** (intet/≤0 = alle):
```bash
mvn exec:java                          # alle 150k (nat-kørsel)
mvn exec:java -Dexec.args="200"        # test: kun 200 symboler
```

Nat-backfill: `Ingest` er default-`mainClass`, så HTPC kan køre `mvn exec:java` over natten.

---

## Screener-web-portal (`screener/`) — det centrale produkt

PHP+MySQL-webportal der lader brugeren filtrere ~76k aktier på sliders (med histogrammer),
sammenligne på base-100-grafer, og åbne teknisk analyse (SMA/RSI/MACD/volumen) pr. aktie.

- **`screener/bin/precompute.php`** — bygger den brede, forudberegnede tabel
  `stockOverview_screener` (én række pr. aktie, ~150 kolonner: afkast, CAGR, **quality**
  (annualiseret eksp. hældning × R², Clenow-stil), trend-stabilitet R², volatilitet, maxdd,
  sharpe, beta, **markeds-korrelation² (mkt_r2)**, relativ styrke vs S&P500 — pr. vindue
  1M…10Y). Metrics-vinduer er ≤10 år; web-portalen fuldscanner tabellen på ms.
  Korrupte/umulige værdier clampes til null (fx |quality|>10, |sharpe|>10, |beta|>5).
  Til sidst: **dedup** (markér krydsnoteringer → `is_primary`) + **facet-cache** (histogrammer).
- **`screener/bin/dedup.php`** — samme selskab på flere børser (WDC, WDC.F, WDC.MX…) →
  vælg ét primært listing pr. (navn, land) via børs-hierarki (US > Norden/V.Europa > … > OTC).
  Web viser `is_primary=1` som standard; toggle "Vis alle børsnoteringer" viser alle.
- **`screener/web/`** — PHP-API (`api.php`) + frontend (`screener.php` + `assets/screener.js`).
  Gemte screens/favoritter/skjulte ligger i DB (`stockOverview_userdata`), pt. GLOBALT delt
  (`ownerToken()` returnerer fast `'global'`, intet login). Facetter caches i `stockOverview_cache`.
- PHP-config: `screener/config/config.php` (gitignored). Verifikation: headless-Chrome-
  screenshot mod `http://localhost/screener/...` (Apache serverer det); interaktiv test via
  CDP (`--remote-allow-origins=*`, Python venter — bash `sleep` er blokeret i sandkassen).

## Daglig drift — `daily_update.sh` (cron kl. 04:00)

```bash
0 4 * * * /home/allan/stockOverview/daily_update.sh >> .../daily_cron.log 2>&1
```
1. **Inkrementel ingest** (`INGEST_FORCE=1 mvn compile exec:java`) — nye dagskurser for alle
   gyldige symboler (~110 min, Yahoo-rate-limit). `INGEST_FORCE=1` ignorerer resume-skip så
   ALLE opdateres.
2. **`precompute.php`** (~50 min) — genberegner screener-tabel + dedup + facet-cache.

Logger til `logs/daily_update.log`. Tunge fuld-backfills er IKKE en del af cron — kør manuelt
med `INGEST_BACKFILL_ALL=1`. (Ligger i crontab ved siden af CarCrawler 20:00 + stocks 22:30.)

---

## Momentum-screener (gammel) — kør-flow

```
allYahooStocks.txt
      ↓
YahooStockFetcher      (henter 200 dages historik via Yahoo Finance API)
      ↓
StockDb.persist        (UPSERT kurser + symboler → MariaDB, tabeller stockOverview_*)
      ↓
StockFilterHelper      (fjerner aktier med market cap < 1.000.000)
      ↓
AllansStrategy         (scorer alle aktier, momentum-vægtet, normaliseret)
      ↓
GoogleStockCopyPasteLinkGenerator  (printer links til top-aktier)
      ↓
ExcelGenerator         (CSV med base-100 indekseret historik for top 10)
```

Main entry point: `dk.stockAnalyzer.Main`

Default parametre: 200 dages historik, weightFactorPlus=2, weightFactorMinus=2.

`usePersistedFile=true` i `Main` springer Yahoo-fetch over og kører på data fra DB
(`StockDb.load`) i stedet — nyttigt til at gen-score uden at hamre Yahoo.

### Historik-tærskel (≥70%)

Det fulde vindue er `daysHistory+2` (= 202) handelsdage. Aktier accepteres hvis de
har mindst **70%** af det (`YahooStockFetcher.MIN_HISTORY_FRACTION`, ≥142 punkter) —
ikke kun fuld historik. Kortere historik scores over de dage den har, og
`AllansStrategy` **normaliserer** scoren (gennemsnitlig vægtet dagsbevægelse i stedet
for sum), så lange og korte historikker er sammenlignelige. `ExcelGenerator`
dimensionerer efter den længste historik i top-10 og udfylder manglende dage med blank.

---

## AllansStrategy — scoring-algoritme

For hver aktie, for hver dag i de seneste N dage:
- Beregn daglig pct-ændring: `(pris[i] / pris[i+1] - 1) * 100`
- Vægt faktoren aftager lineært fra 2.0 (nyeste dag) til 1.0 (ældste dag)
- Positive dage ganges med `currentWeightFactorPlus`
- Negative dage ganges med `currentWeightFactorPlus * weightFactorMinus`
- Aktier med daglig ændring > ±100% eller præcis 0% kasseres (score = -111)

Resultatet: aktier der har klaret sig godt **for nylig** scores højere end dem der klarer sig jævnt over hele perioden.

---

## Byg og kør

```bash
mvn compile                                   # bygger (kun dk/stockAnalyzer/** kompileres)
mvn exec:java                                 # kører default-mainClass = dk.stockAnalyzer.Ingest (alle)
mvn exec:java -Dexec.args="200"               # Ingest med limit
mvn exec:java -Dexec.mainClass=dk.stockAnalyzer.Main   # kør momentum-screeneren i stedet
```

Kræver **JDK 17+** (kompileres med `maven.compiler.release=17`). CSV-output (momentum) lander i `output/`; alt kursdata + fundamentals persisteres i MariaDB (se nedenfor).

## Datapersistens — MariaDB (vigtigt)

Al data gemmes i MariaDB via `dk.stockAnalyzer.StockDb` (JDBC, MariaDB-driver). Den gamle
Java-serialisering til `.bin` (`PortefolioPersister`) er **fjernet**. Vi genbruger
`stocks`-projektets MariaDB på HTPC; tabeller er prefixet `stockOverview_`. `StockDb`
opretter dem selv (`CREATE TABLE IF NOT EXISTS`); alt bruger `ON DUPLICATE KEY UPDATE`.

| Tabel | Indhold |
|---|---|
| `stockOverview_securities` | symbol (PK), name, exchange, currency, quote_type, **sector, industry, country**, employees, website, business_summary, first_seen, last_updated |
| `stockOverview_prices` | symbol, price_date, open, high, low, close, **adj_close**, volume — PK(symbol, price_date) |
| `stockOverview_dividends` | symbol, ex_date, amount |
| `stockOverview_splits` | symbol, split_date, numerator, denominator |
| `stockOverview_fundamentals` | symbol, snapshot_date, **~45 nøgletal-kolonner** (trailing/forward PE, P/B, EV/EBITDA, ROE, ROA, margins, debt_to_equity, current/quick ratio, growth, dividend_yield, beta, 52w, analyst-targets …) + `raw_json` (hele quoteSummary). PK(symbol, snapshot_date) → tidsserie |
| `stockOverview_ingest_log` | symbol (PK), last_price_date, last_fundamentals_date, price_points, status (`ok`/`not_found`/`backfilled`), error — resumerbarhed |
| `stockOverview_symbols` | **gammel** momentum-tabel (symbol, name, market_cap); bevares til `Main`/`StockDb.persist` |
| `stockOverview_screener` | **forudberegnet** bred tabel (én række/aktie, ~150 kolonner) bygget af `precompute.php`. + `is_primary`/`primary_symbol`/`listing_count` (dedup). Web-portalen fuldscanner denne |
| `stockOverview_indexes` | benchmark-indeks (^GSPC, ^DJI, ^IXIC) til relativ styrke / mkt_r2 / graf-overlay |
| `stockOverview_fx` | valutakurser → USD (markedsværdi sammenlignes på tværs af børser) |
| `stockOverview_cache` | nøgle/værdi JSON-cache (fx forudberegnede facetter/histogrammer) — så web svarer på ms i st.f. ~8s |
| `stockOverview_userdata` | per-ejer gemte screens / favorit-filtre / skjulte graf-aktier (pt. ejer=`'global'`) |
| `stockOverview_runs` | log over precompute/ingest-kørsler |

`fundamentals` er bred + rå JSON: de vigtigste felter som søgbare kolonner
(`WHERE trailing_pe < 15 AND return_on_equity > 0.15`), plus hele quoteSummary-svaret i
`raw_json` så intet går tabt og flere felter kan udtrækkes senere uden re-scrape.

**Credentials:** `config/db.properties` (gitignored — se `config/db.properties.example`),
samme bruger/password som `stocks`-projektet. Override via env
`STOCKOVERVIEW_DB_URL` / `_USER` / `_PASS`.

### Remote-adgang (MySQL Workbench fra Windows)

MariaDB på HTPC er åbnet på LAN (juni 2026): `bind-address = 0.0.0.0`
(`/etc/mysql/mariadb.conf.d/50-server.cnf`, backup `.bak.*` ved siden af), ufw tillader
3306 kun fra `192.168.1.0/24`, og brugeren `stocks@'192.168.1.%'` (samme password som
lokalt) har adgang til `stocks`-DB'en. Workbench: host **192.168.1.85**, port 3306, user
`stocks`, db `stocks`. (HTPC's LAN-IP er 192.168.1.85 på `enp3s0`.)

## Yahoo-dataadgang (vigtigt)

Al datahentning går gennem `dk.stockAnalyzer.YahooClient` (ren Java, `HttpURLConnection` + Jackson). Yahoo kræver siden 2023 cookie + crumb:
1. cookie: `GET https://fc.yahoo.com` (følg IKKE redirects — `Set-Cookie` er på det direkte svar)
2. crumb: `GET https://query1.finance.yahoo.com/v1/test/getcrumb` med cookien
3. data:
   - **priser:** `v8/chart/<symbol>?period1=<fra>&period2=<now>&interval=1d&events=div,split`
     (OHLCV + adjclose + udbytte/split-events) — `YahooClient.getDailyHistory`. `fra=0` =
     hele historikken (fuld backfill); ellers sidste dato − 4 dages overlap. **IKKE `range=max`**
     (returnerede månedlige fantom-bjælker — se gotcha i ingest-sektionen). Weekend-bjælker droppes.
   - **fundamentals:** `v10/quoteSummary/<symbol>?modules=price,summaryProfile,summaryDetail,defaultKeyStatistics,financialData&crumb=` — `YahooClient.getQuoteSummary`. Tal er wrappet som `{raw, fmt}`; `StockDb` læser `.raw`.
   - **batch-snapshot (momentum):** navn+marketCap via `v7/quote?...&crumb=`

**Gotcha:** `getcrumb` afviser en detaljeret browser-User-Agent (fuld Chrome-streng) med HTTP 429, men accepterer den generiske `"Mozilla/5.0"` — så YahooClient bruger bevidst den korte UA. `getcrumb` har en stram rate-limit; den kaldes derfor kun én gang pr. kørsel (caches). `YahooClient.rateLimitMs` (default 1500 ms) + 429-backoff styrer kadencen — sæt lavere for fart, men pas på penalty-box.

## Modernisering (status)

**✅ Gjort:**
1. **Maven `pom.xml`** — `sourceDirectory=src`, yahoofinance-api 3.17.0 + jackson-databind 2.18.2. Compiler-plugin kompilerer kun `dk/stockAnalyzer/**` (holder den gamle 2009-kode i dk/skov m.fl. ude).
2. **Windows-stier fjernet** — `YahooStockFetcher` læser `doc/allYahooStocks.txt`, `ExcelGenerator` skriver `output/stockScreener.csv` (opretter mappen). Plus `YYYY`→`yyyy`-datofix.
3. **Crumb/cookie løst** — ny `YahooClient` (se ovenfor). `YahooStockFetcher` omskrevet til den; bygger historik-map med index 0 = nyeste (det `AllansStrategy` forventer). En gammel loop-bug (sprang første symbol over, tilføjede `null`) er rettet.
4. **Rate limiting** — adaptiv AIMD i `YahooClient` (`currentDelayMs`, floor `MIN_DELAY_MS`=40 ms, loft 4000 ms) + 429-backoff.
7. **DB-persistens** — kurser i MariaDB via `StockDb` (afløser `.bin`-serialisering). ≥70%-historik-tærskel + normaliseret score.
5. **(Delvist) links** — navn/marketCap kommer nu fra `v7/quote`. `GoogleStockCopyPasteLinkGenerator` peger stadig på lukkede `google.com/finance`-links; erstat evt. med `https://finance.yahoo.com/quote/<symbol>` eller TradingView (`https://www.tradingview.com/chart/?symbol=CPH:<symbol-uden-.CO>`).

**⏳ Udestår:**
6. **Aktieliste — `doc/allYahooStocks.txt`** er udvidet til ~150k symboler (global). Kan
   evt. opdateres/udvides fra: **US:** `ftp://ftp.nasdaqtrader.com/SymbolDirectory/`;
   **globalt:** Yahoos uofficielle screener-endpoint; **Norden:** `nasdaqomxnordic.com/aktier`.
7. **aogj.com-migration** — flyt screener-web-portalen til one.com (PHP+MySQL). HTPC forbliver
   compute-motor og pusher data via chunked JSON POST. Husk også `_screener`, `_cache`,
   `_userdata`, `_indexes`, `_fx`-tabellerne.

---

## Projekt-struktur

```
stockOverview/
├── src/
│   ├── dk/stockAnalyzer/     ← AKTIV kode, alt kører herfra
│   │   ├── Main.java
│   │   ├── AllansStrategy.java
│   │   ├── Ingest.java         ← NY: primær data-ingestion (10y OHLCV + fundamentals → DB)
│   │   ├── YahooClient.java    ← crumb/cookie + v8/chart (10y OHLCV) + v10/quoteSummary + v7/quote
│   │   ├── YahooStockFetcher.java
│   │   ├── StockWrapper.java
│   │   ├── StockFilterHelper.java
│   │   ├── ExcelGenerator.java
│   │   ├── GoogleStockCopyPasteLinkGenerator.java
│   │   ├── StockDb.java         ← NY: MariaDB-persistens (afløser PortefolioPersister)
│   │   └── TestStock.java
│   ├── dk/skov/              ← GAMMELT, brug ikke (2009-era, bruger defunct CSV-endpoint)
│   ├── dk/momentumStock/     ← Alternativ momentum-implementering, ikke brugt
│   └── dk/techan/            ← HTML chart-generator, ikke brugt
├── screener/                 ← NY: PHP+MySQL screener-web-portal (det centrale produkt)
│   ├── bin/precompute.php    ← bygger stockOverview_screener + dedup + facet-cache
│   ├── bin/dedup.php         ← markér krydsnoteringer (is_primary)
│   ├── web/                  ← api.php + screener.php + assets/ (frontend)
│   └── config/config.php     ← PHP DB-config (gitignored)
├── daily_update.sh           ← NY: natlig cron (inkrementel ingest + precompute)
├── pom.xml                    ← Maven build (sourceDirectory=src)
├── lib/                       ← gamle jars; ikke længere på classpath (Maven styrer deps)
├── config/
│   ├── db.properties.example ← skabelon for DB-credentials (committet)
│   └── db.properties         ← rigtige credentials (gitignored, Java-ingest)
├── doc/
│   ├── allYahooStocks.txt    ← ~150k symboler (heraf ~73k 'not_found', ~77k gyldige)
│   └── todo.txt
├── output/                    ← runtime-output (CSV), gitignored
├── out/                       ← gammel IntelliJ-output, gitignored
└── stocks3.iml                ← IntelliJ project fil
```

---

## Kendte fejl / gotchas

- **Fantom-månedsbjælker (løst):** `range=max` lagde månedlige bjælker (d. 1. i hver mdr,
  måneds-volumen, også weekender) oveni de daglige → fantom-spikes i volumen/kurs + spurious
  måneds-afkast der oppustede volatilitet/metrics. Løst med `period1=0` + weekend-skip i
  parseren. Eksisterende fantomer slettet (`DELETE … WHERE DAYOFMONTH(price_date)=1`, pr.
  symbol) + precompute genkørt.
- **Korrupte metrics clampes** i `precompute.php`: penny-/illikvide aktier gav absurde værdier
  (quality i 100.000'er, sharpe i mia.) → |quality|>10, |sharpe|>10, |beta|>5, umulige
  fundamentals nulstilles, så de ikke dominerer sorteringen.
- **Store DELETEs:** PK er (symbol, price_date), så `WHERE DAYOFMONTH(...)`/dato-filtre = fuld
  scan. Slet pr. symbol (autocommit, genoptag-bart) i st.f. én kæmpe-transaktion — en dræbt
  baggrunds-DELETE ruller tilbage og holder låse.
- `ExcelGenerator.java` brugte `SimpleDateFormat("YYYY-MM-dd")` (`YYYY` = ugebaseret ISO-år) — rettet til `yyyy-MM-dd`.
- `AllansStrategy.java` bruger `TreeMap` som score-nøgle — hvis to aktier får samme score tilføjes en lille tilfældig decimal for at undgå kollision. Det er primitivt men fungerer.
- Kurser persisteres nu i MariaDB (`StockDb`), ikke længere Java-serialisering til `.bin`. `StockWrapper.implements Serializable` er ikke længere nødvendigt for persistens (men skader ikke).
- `StockFilterHelper` itererer `stocks` og fjerner fra `newStockList` under iteration — fungerer fordi der itereres over den originale liste. Ikke elegant men korrekt.

---

## Java-version

Bygges med `maven.compiler.release=17` (sat i pom.xml). Brug JDK 17 eller 21 LTS. (Oprindeligt skrevet til Java 8 / source 1.6.)
