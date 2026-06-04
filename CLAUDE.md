# StockOverview — Projekt-kontekst (2026)

## Hvad er dette?

En Java-baseret aktie-screener der henter historiske kursdata fra Yahoo Finance, kører "Allan's Strategy" (momentum-baseret scoring) på alle aktier, og outputter en rangliste samt Excel-export af top-scorerne.

Projektet er skrevet i 2015–2017. Status juni 2026: **moderniseret og kørbart** — bygger med Maven og henter live Yahoo-data (valideret end-to-end på NOVO-B/MAERSK-B/DSV). Se "Modernisering (status)" nedenfor for hvad der er gjort, og hvad der udestår.

---

## Kør-flow

```
allYahooStocks.txt
      ↓
YahooStockFetcher      (henter 200 dages historik via Yahoo Finance API)
      ↓
StockFilterHelper      (fjerner aktier med market cap < 1.000.000)
      ↓
AllansStrategy         (scorer alle aktier, momentum-vægtet)
      ↓
GoogleStockCopyPasteLinkGenerator  (printer links til top-aktier)
      ↓
ExcelGenerator         (CSV med base-100 indekseret historik for top 10)
```

Main entry point: `dk.stockAnalyzer.Main`

Default parametre: 200 dages historik, weightFactorPlus=2, weightFactorMinus=2.

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
mvn exec:java                                 # kører dk.stockAnalyzer.Main
mvn exec:java -DmainClass=<anden.Main.Class>  # kør en anden klasse
```

Kræver **JDK 17+** (kompileres med `maven.compiler.release=17`). GitHub Codespaces har en JDK forudinstalleret. Output (CSV + `.bin`) lander i `output/`.

## Yahoo-dataadgang (vigtigt)

Al datahentning går gennem `dk.stockAnalyzer.YahooClient` (ren Java, `HttpURLConnection` + Jackson). Yahoo kræver siden 2023 cookie + crumb:
1. cookie: `GET https://fc.yahoo.com` (følg IKKE redirects — `Set-Cookie` er på det direkte svar)
2. crumb: `GET https://query1.finance.yahoo.com/v1/test/getcrumb` med cookien
3. data: historik via `v8/chart`, navn+marketCap via `v7/quote?...&crumb=`

**Gotcha:** `getcrumb` afviser en detaljeret browser-User-Agent (fuld Chrome-streng) med HTTP 429, men accepterer den generiske `"Mozilla/5.0"` — så YahooClient bruger bevidst den korte UA. `getcrumb` har en stram rate-limit; den kaldes derfor kun én gang pr. kørsel (caches). `YahooClient.rateLimitMs` (default 1500 ms) + 429-backoff styrer kadencen — sæt lavere for fart, men pas på penalty-box.

## Modernisering (status)

**✅ Gjort:**
1. **Maven `pom.xml`** — `sourceDirectory=src`, yahoofinance-api 3.17.0 + jackson-databind 2.18.2. Compiler-plugin kompilerer kun `dk/stockAnalyzer/**` (holder den gamle 2009-kode i dk/skov m.fl. ude).
2. **Windows-stier fjernet** — `YahooStockFetcher` læser `doc/allYahooStocks.txt`, `ExcelGenerator` skriver `output/stockScreener.csv` (opretter mappen), `.bin` i `output/`. Plus `YYYY`→`yyyy`-datofix.
3. **Crumb/cookie løst** — ny `YahooClient` (se ovenfor). `YahooStockFetcher` omskrevet til den; bygger historik-map med index 0 = nyeste (det `AllansStrategy` forventer). En gammel loop-bug (sprang første symbol over, tilføjede `null`) er rettet.
4. **Rate limiting** — `YahooClient.rateLimitMs` + 429-backoff.
5. **(Delvist) links** — navn/marketCap kommer nu fra `v7/quote`. `GoogleStockCopyPasteLinkGenerator` peger stadig på lukkede `google.com/finance`-links; erstat evt. med `https://finance.yahoo.com/quote/<symbol>` eller TradingView (`https://www.tradingview.com/chart/?symbol=CPH:<symbol-uden-.CO>`).

**⏳ Udestår:**
6. **Aktieliste — `doc/allYahooStocks.txt`** (25.242 symboler fra ~2015, mange udgåede). Opdateret liste kan hentes fra:
   - **US (NASDAQ/NYSE):** `ftp://ftp.nasdaqtrader.com/SymbolDirectory/` — gratis, dagsopdateret
   - **Globalt via Yahoo screener:** `https://query1.finance.yahoo.com/v1/finance/screener` (uofficiel, kræver crumb)
   - **Nasdaq Copenhagen (.CO):** `https://www.nasdaqomxnordic.com/aktier` — manuelt download

   NB: fuld kørsel = 25.242 symboler × `rateLimitMs` → mange timer. Test på en kort liste først, eller skru på `rateLimitMs`.

---

## Projekt-struktur

```
stockOverview/
├── src/
│   ├── dk/stockAnalyzer/     ← AKTIV kode, alt kører herfra
│   │   ├── Main.java
│   │   ├── AllansStrategy.java
│   │   ├── YahooClient.java   ← NY: crumb/cookie + v8/chart + v7/quote
│   │   ├── YahooStockFetcher.java
│   │   ├── StockWrapper.java
│   │   ├── StockFilterHelper.java
│   │   ├── ExcelGenerator.java
│   │   ├── GoogleStockCopyPasteLinkGenerator.java
│   │   ├── PortefolioPersister.java
│   │   └── TestStock.java
│   ├── dk/skov/              ← GAMMELT, brug ikke (2009-era, bruger defunct CSV-endpoint)
│   ├── dk/momentumStock/     ← Alternativ momentum-implementering, ikke brugt
│   └── dk/techan/            ← HTML chart-generator, ikke brugt
├── pom.xml                    ← Maven build (sourceDirectory=src)
├── lib/                       ← gamle jars; ikke længere på classpath (Maven styrer deps)
├── doc/
│   ├── allYahooStocks.txt    ← 25.242 symboler (fra ~2015, forældet)
│   └── todo.txt
├── output/                    ← runtime-output (CSV + .bin), gitignored
├── out/                       ← gammel IntelliJ-output, gitignored
└── stocks3.iml                ← IntelliJ project fil
```

---

## Kendte fejl / gotchas

- `ExcelGenerator.java` brugte `SimpleDateFormat("YYYY-MM-dd")` (`YYYY` = ugebaseret ISO-år) — rettet til `yyyy-MM-dd`.
- `AllansStrategy.java` bruger `TreeMap` som score-nøgle — hvis to aktier får samme score tilføjes en lille tilfældig decimal for at undgå kollision. Det er primitivt men fungerer.
- `PortefolioPersister` bruger Java-serialisering. Ændringer i `StockWrapper.java` vil gøre gemte `.bin`-filer ulæselige.
- `StockFilterHelper` itererer `stocks` og fjerner fra `newStockList` under iteration — fungerer fordi der itereres over den originale liste. Ikke elegant men korrekt.

---

## Java-version

Bygges med `maven.compiler.release=17` (sat i pom.xml). Brug JDK 17 eller 21 LTS. (Oprindeligt skrevet til Java 8 / source 1.6.)
