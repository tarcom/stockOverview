# StockOverview — Projekt-kontekst (2026)

## Hvad er dette?

En Java-baseret aktie-screener der henter historiske kursdata fra Yahoo Finance, kører "Allan's Strategy" (momentum-baseret scoring) på alle aktier, og outputter en rangliste samt Excel-export af top-scorerne.

Projektet er skrevet i 2015–2017 og er ikke kørt siden. Status juni 2026: **ikke kørbart uden modernisering** (se nedenfor).

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

## Hvad skal moderniseres (prioriteret)

### 1. Build-system → Maven `pom.xml` (mangler!)
Projektet bruger kun en IntelliJ `.iml`-fil. Skal have en `pom.xml`.

```xml
<dependency>
    <groupId>com.yahoofinance-api</groupId>
    <artifactId>YahooFinanceAPI</artifactId>
    <version>3.17.0</version>
</dependency>
```

Konfigurer source directory til `src` (ikke standard Maven-layout):
```xml
<build>
    <sourceDirectory>src</sourceDirectory>
</build>
```

### 2. Hardkodede Windows-stier (krasjer på Linux/Mac)

**`YahooStockFetcher.java` linje 63:**
```java
// FØR (Windows-only):
new FileReader("C:\\projects\\stockOverview\\doc\\allYahooStocks.txt")

// EFTER (cross-platform):
new FileReader("doc" + File.separator + "allYahooStocks.txt")
```

**`ExcelGenerator.java` linje 74:**
```java
// FØR:
String filename = "C:\\Projects\\stockOverview\\excelGenerator.csv";

// EFTER:
String filename = "output" + File.separator + "stockScreener.csv";
// Husk at oprette output-mappen: new File("output").mkdirs();
```

**`PortefolioPersister.java`** — filnavnet `"PersistedStocks.bin"` er allerede relativt, men brug `output/PersistedStocks.bin` for orden skyld.

### 3. Yahoo Finance API — mulig crumb/cookie-problematik

Yahoo Finance begyndte i 2023 at kræve en crumb-token + cookies for API-kald. `YahooFinanceAPI 3.17.0` (feb 2022) håndterer det muligvis ikke.

**Test dette først** — hvis historikdata kan hentes, er vi kørende. Fejler det med 401/403, skal der enten:
- Bruges en nyere fork af biblioteket
- Eller tilføjes manuel crumb-hentning via HTTP-kald til `https://query1.finance.yahoo.com/v1/test/getcrumb` med cookies

### 4. Rate limiting

`YahooStockFetcher.getStock()` kalder Yahoo Finance API sekventielt for 25.000+ aktier. Tilføj:
```java
private static final int RATE_LIMIT_MS = 100; // juster efter behov
// ...i getStock()-loop:
Thread.sleep(RATE_LIMIT_MS);
```

Sæt til 0 for fuld hastighed (web-kald er flaskehalsen uanset hvad).

### 5. Google Finance links → Yahoo Finance links

`GoogleStockCopyPasteLinkGenerator.java` genererer links til `google.com/finance` som er lukket siden 2020. Linegeneratoren bruges kun til `.CO`-aktier (Nasdaq Copenhagen).

Erstat med Yahoo Finance-links:
```java
// Individuel aktie:
"https://finance.yahoo.com/quote/" + symbol

// Eller TradingView watchlist (understøtter multi-symbol):
"https://www.tradingview.com/chart/?symbol=CPH:" + symbol.replace(".CO", "")
```

### 6. Aktieliste — `doc/allYahooStocks.txt`

Listen med 25.242 symboler er fra ~2015 og indeholder mange udgåede aktier. En opdateret liste kan hentes fra:

- **US-aktier (NASDAQ/NYSE):** `ftp://ftp.nasdaqtrader.com/SymbolDirectory/` — gratis, dagsopdateret
- **Globalt via Yahoo Finance screener:** `https://query1.finance.yahoo.com/v1/finance/screener` (uofficiel, kræver crumb)
- **Nasdaq Copenhagen (.CO):** `https://www.nasdaqomxnordic.com/aktier` — manuelt download

---

## Projekt-struktur

```
stockOverview/
├── src/
│   ├── dk/stockAnalyzer/     ← AKTIV kode, alt kører herfra
│   │   ├── Main.java
│   │   ├── AllansStrategy.java
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
├── lib/
│   ├── YahooFinanceAPI-3.6.1.jar   ← erstattes af Maven-dependency
│   └── YahooFinanceAPI-2.0.0.jar   ← kan slettes
├── doc/
│   ├── allYahooStocks.txt    ← 25.242 symboler (fra ~2015, forældet)
│   └── todo.txt
├── out/                       ← compiled output, kan slettes/gitignores
└── stocks3.iml                ← IntelliJ project fil
```

---

## Kendte fejl / gotchas

- `ExcelGenerator.java` bruger `SimpleDateFormat("YYYY-MM-dd")` — `YYYY` er ugebaseret år (ISO week). Brug `yyyy-MM-dd`.
- `AllansStrategy.java` bruger `TreeMap` som score-nøgle — hvis to aktier får samme score tilføjes en lille tilfældig decimal for at undgå kollision. Det er primitivt men fungerer.
- `PortefolioPersister` bruger Java-serialisering. Ændringer i `StockWrapper.java` vil gøre gemte `.bin`-filer ulæselige.
- `StockFilterHelper` itererer `stocks` og fjerner fra `newStockList` under iteration — fungerer fordi der itereres over den originale liste. Ikke elegant men korrekt.

---

## Java-version

Kompileret med Java 8 (source/target 1.6). Fungerer fint med Java 11+. Brug Java 17 eller 21 LTS.
