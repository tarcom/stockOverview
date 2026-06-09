# StockScreener — plan & status

En moderne aktie-screener i ånden fra den gamle Google Stock Screener (sliders + histogrammer),
bygget helt fra bunden. Kører på HTPC i **PHP + MySQL** (kan flyttes til one.com/aogj.com senere).
Den læser de `stockOverview_*`-tabeller som Java-ingesten fylder, og bygger oven på dem.

## Arkitektur

```
Java-ingest (data-motor)          →  fuld MariaDB: prices (10y OHLCV), fundamentals, …
   ↓ bin/precompute.php (PHP CLI)
stockOverview_screener            →  ÉN række pr. aktie, alt forudberegnet
   ↓                                  (metadata + ~24 fundamentals + 12 metrics × 8 vinduer)
web/ (PHP-portal)                 →  fuldscanner ~150k rækker på millisekunder
```

Portalen rører **aldrig** rå dagshistorik ved filtrering — kun den brede, forudberegnede tabel.
Alt i **USD** (FX-konverteret), så aktier kan sammenlignes på tværs af børser.

## Kerne-metrics (pr. tidsvindue: 1M, 3M, 6M, 1Y, 2Y, 3Y, 5Y, 10Y)

| Metric | Betydning |
|---|---|
| `ret` / `cagr` | samlet / annualiseret afkast |
| **`quality`** | **annualiseret eksponentiel hældning × R²** — stabil + høj vækst (Clenow-stil). Default-sortering. |
| `trend_r2` | hvor jævnt aktien følger sin egen vækstkurve (log-lineær fit) |
| `lin_r2` | samme på lineær akse (medtaget til sammenligning) |
| `vol` / `maxdd` / `sharpe` | volatilitet / max drawdown / risikojusteret afkast |
| `beta` / `mkt_r2` | følsomhed + korrelation² mod S&P500 ("i takt med markedet") |
| `idio_vol` | idiosynkratisk volatilitet (den del der IKKE forklares af markedet = "tilfældig") |
| `rs` | relativ styrke: merafkast vs S&P500 |

Plus datakvalitet: `history_years`, `max_day_move` (luger korrupt Yahoo-data fra).

## Faser

- [x] **Fase 0 — datafundament** ✅
  - [x] Benchmark-indeks (S&P500, NASDAQ, Dow) + FX-kurser → USD (`bin/fetch_benchmarks.php`)
  - [x] Skema `stockOverview_screener` (bred) + `_runs` + `_indexes` + `_fx`
  - [x] Precompute-motor med alle metrics (`bin/precompute.php`)
  - [x] Datakvalitets-værn (`max_day_move`)
  - [ ] Fuld precompute-kørsel (afventer at ingesten bliver færdig — kører ~100 min)
- [x] **Fase 1 — portal-skelet + "Universet"** ✅
  - [x] PHP-portal (`web/index.php`), mørkt tema, KPI-tal + kørsels-status (ingest + precompute)
  - [x] Dashboard-grafer (Chart.js): sektorer, lande, industrier, type, datakvalitet
  - [x] `PLAN.md` renderet nederst på siden (egen minimal markdown-renderer)
  - [ ] Repræsentativt fuldt datasæt (afventer fuld precompute efter ingesten)
- [x] **Fase 2 — filter-motoren** ✅
  - [x] 48 range-filtre + 3 multivalg, grupperet i 8 foldbare kategorier (PriceRunner-stil)
  - [x] Dual-range sliders med **histogram bagved** (outlier-robust, log-skala hvor relevant)
  - [x] Live tæller + **funnel** (udfaldsrum snævres ind pr. filter) + sortering/antal/retning
  - [x] "i"-hjælpeikoner (mouse-over) på alle filtre + **delbar URL** (filter-state i query)
  - [x] Datakvalitets-default: skjuler >100%/dag-spikes; `api.php` (facets + query)
  - [ ] Re-filtrerede histogrammer (pt. globale); facet-caching ved fuldt datasæt
- [x] **Fase 3 — udfaldsrum** ✅
  - [x] Top-N tabel med sortering
  - [x] **Overlay base-100 graf** af de viste aktier + **benchmark-overlay** (S&P500/NASDAQ/Dow), valgbart tidsinterval (1M…10Y), klikbar forklaring
  - [x] **Enkelt-aktie fokus-view** (klik på række): base-100 kursgraf med **SMA 20/50/100/200** + benchmark-overlay, og sub-pane med **RSI / MACD / volumen** — alt med mouse-over-forklaringer (`api.php?action=stock`, indikatorer beregnes klientside)
- [x] **Fase 4 — strategi-presets** ✅
  - [x] Ét-klik presets: 📈 Stabil compounder · 🚀 Momentum · 💰 Value · ⭐ Kvalitet · 🏦 Udbytte (nulstiller + sætter hel filter-opskrift + sortering, kan finjusteres bagefter)
  - [x] `?preset=NAVN` deep-link; aktiv preset fremhæves
  - [x] Multi-sammenligning dækkes af overlay base-100-grafen (de viste aktier på én graf)
  - [ ] (Polish-backlog) vælg specifikke rækker til sammenligning; aktiv-filter-chips

## Fase 5 — aogj.com-migration (✅ GENNEMFØRT 2026-06-09; prices-backfill kører)

Portalen er **live offentligt** på **https://aogj.com/screener/screener.php** (PHP 8.5.6 +
MariaDB 10.11.16 på one.com). Web-filer + alle små tabeller er migreret; filtrering, dashboard,
facetter, presets og CSV virker. Den fulde `prices`-backfill (option A — hele historikken,
260M rækker, ~24,7 GB) blev startet 2026-06-09 og kører i baggrunden (~21-24k rk/s, ETA ~3,5t,
log: `screener/logs/push_prices.log`) — graferne (kursoverlay + enkelt-aktie TA) bliver fulde
når den er færdig.

**Sådan blev det gjort (faktisk implementering):**
- `deploy/ingest.php` (modtager) generaliseret: whitelist på **tabel-NAVNE** (prices, screener,
  securities, indexes, fx, cache, userdata, ingest_log, runs); skemaet sendes af pusheren
  (`SHOW CREATE TABLE`), normaliseres (`current_timestamp()`→`CURRENT_TIMESTAMP`) + gøres
  idempotent; insert-kolonner valideres dynamisk mod den faktiske tabel. Nye actions: `truncate`,
  og `ping` returnerer nu php+mysql-version.
- `bin/push_to_aogj.php` generaliseret: virker for enhver `stockOverview_*`-tabel, udleder
  kolonner automatisk, **UBUFFERET** streaming (kritisk for 260M prices → ingen OOM), sender DDL
  selv. Env-knapper: `PUSH_SINCE=YYYY-MM-DD` (nyere historik), `PUSH_FROM_SYMBOL` (resume,
  keyset på PK), `PUSH_NO_DDL=1`.
- `web/lib/db.php`: config-sti prøver nu både repo-layout (`../../config`) og aogj-layout
  (`../config`) → web deployes som `/screener/` med config i `/screener/config/config.php`.
- aogj-config: `deploy/config.aogj.php` (gitignored) → FTP'et til `/screener/config/config.php`
  (peger på aogj's egen MySQL `aogj_com`).
- Deploy: direkte FTPS fra HTPC via `curl --ssl-reqd` med CarCrawler2's `.ftp-credentials`
  (`ftp.aogj.com`). Ingen GitHub Actions-workflow nødvendig.
- Daglig delta-sync tilføjet til `daily_update.sh` (trin 3): re-push af screener/securities/
  facetter/status + nye prices (`PUSH_SINCE=7 dage`) efter nat-precompute.
- **Sprunget over** (web rører dem ikke): `fundamentals` (3,7 GB), `dividends`, `splits`,
  `symbols`. TA-modalens fundamentals kommer fra `screener`-tabellen + `securities.business_summary`.
- `PLAN.md` deployes **ikke** til aogj (nævner Yahoo) → plan-siden renderer tomt kort offentligt.

---

### Oprindelig plan (bevaret som reference)

Flyt portalen fra HTPC til **one.com/aogj.com** så den er offentligt tilgængelig.

**Arkitektur (besluttet):** HTPC forbliver **compute-motoren** (Java-ingest + `precompute.php`
bygger tabellerne i MariaDB). one.com tillader **IKKE** remote MySQL, så HTPC **pusher**
tabeller op via **chunked JSON POST** → en token-beskyttet modtager på aogj, der UPSERTer til
aogj-DB'en. aogj kører så portalen **standalone** (PHP 8.5 + egen MySQL).

```
HTPC: MariaDB  → bin/push_to_aogj.php (chunked JSON POST, batch ~25k rækker)
                      ↓ HTTPS + token
aogj: screener/ingest.php (modtager, whitelistet DDL + bulk UPSERT) → aogj-MySQL
aogj: screener/web/ (PHP-portal, læser aogj-MySQL)  ← brugeren
```

**Scaffolding (findes):**
- `bin/push_to_aogj.php` — streamer en tabel op i batches (ping → ddl → insert), måler tempo.
- `deploy/ingest.php` — **modtageren** (GITIGNORED: aogj-DB-creds + token). LIVE på
  `https://aogj.com/screener/ingest.php` (verificeret: svarer `{"ok":true,"php":"8.5.6"}`).
  `deploy/ingest.example.php` er committet skabelon.
- `deploy/push-config.php` — **GITIGNORED**: `{url, token}` til pusheren.
- ⚠️ **Begge kender pt. KUN `stockOverview_prices`** (PoC, ~200k rækker pushet som test).

**Tabel-størrelser (det der skal pushes):**
| Tabel | Rækker | Størrelse | |
|---|---|---|---|
| `stockOverview_prices` | **260M** | **24,7 GB** | ⚠️ knasten — til grafer |
| `stockOverview_screener` | 67k | 106 MB | kerne (filtrering) |
| `stockOverview_securities` | 71k | 92 MB | navne/sektor/beskrivelse (TA-detalje) |
| `_indexes` / `_fx` / `_cache` / `_userdata` | småt | <1 MB | benchmark/FX/facetter/screens |

**Den store beslutning — `prices` (24,7 GB):** one.com-kvote er 50 GB.
- **A) Push hele** → fuld dyb historik + Maks-grafer, men halvdelen af kvoten + mange-timers
  push + tung 260M-rækkers tabel på shared hosting (risiko for one.com-grænser).
- **B) Push kun nyere (~5-10 år)** → ~5-8 GB, hurtigt/let; Maks-grafer begrænset på aogj.
- **Anbefaling: fasedelt** — push alt UNDTAGEN fuld prices først (filtrering+dashboard+presets
  virker straks), tag så A-vs-B-beslutningen.

**Deploy-mekanisme:** som `stocks` — **GitHub Actions → lftp/FTPS** (`ftp.aogj.com`, secret
`FTP_PASS`). Mangler: workflow i `tarcom/stockOverview` + secret. Alternativ: direkte FTP fra
HTPC (CarCrawler2 har `.ftp-credentials` til aogj). `deploy/ingest.php` kom op manuelt (commit `9746a2e`).

**Udestående trin:**
- [ ] Udvid push + modtager (`push_to_aogj.php` + `deploy/ingest.php`) til alle nødvendige
  tabeller (pt. kun prices): screener, securities, indexes, fx, cache, userdata.
- [ ] Deploy `screener/web/` til aogj (`/screener/`) + en `config.php` på aogj der peger på aogj-DB'en.
- [ ] Push dataen (fase 1: småtabeller; fase 2: prices iht. A/B-beslutning).
- [ ] Daglig delta-sync: efter nat-precompute pusher HTPC den opdaterede `screener` (+ nye prices) til aogj.

## TODO / backlog (bygges i mindre bidder)

- [ ] Tekniske indikatorer på graferne: SMA (20/50/100/200), EMA, Bollinger, RSI, MACD i sub-pane — med mouse-over-forklaringer
- [ ] Benchmark-overlay på alle grafer (vælg S&P500/NASDAQ/Dow at sammenligne mod)
- [ ] **Live Yahoo-charts fravalgt** — vi bruger vores egne data (besluttet). Kan genovervejes hvis vi vil have intradag.
- [ ] Datarensning: håndtér flere korrupte serier (fladlinede spikes, denominerings-skift)
- [ ] Daglig automatik: ingest (top-up) → fetch_benchmarks → precompute via cron
- [ ] `stockOverview_runs`: lad Java-ingesten logge fulde/delvise kørsler (til dashboard-status)
- [ ] Percentil-rang pr. metric (fx "P/E i nederste 10% af sektoren")
- [ ] Likviditetsfiltre (min. pris, min. gns. volumen)
- [ ] Kurater universet til **top 7 lande i EU + top 7 i verden** (relevans/handelbarhed; evt. som standard-filter)
- [x] Detalje-visning: fundamentals + sektor-percentiler + virksomhedsbeskrivelse i TA-modal'en
- [ ] Opret **buymeacoffee.com/noergaard** (linket ligger allerede live på siden — peger pt. på en konto der ikke findes endnu)
