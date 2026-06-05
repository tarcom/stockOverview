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
- [ ] **Fase 3 — udfaldsrum**: base-100-grafer pr. aktie (med S&P500-overlay) + tekniske indikatorer, valgbart tidsinterval (top-N tabel + sortering er på plads)
- [ ] **Fase 4 — presets, sammenligning, polish**: strategi-presets ("Stabil compounder", "Value", "Momentum" …), base-100 multi-sammenligning

## TODO / backlog (bygges i mindre bidder)

- [ ] Tekniske indikatorer på graferne: SMA (20/50/100/200), EMA, Bollinger, RSI, MACD i sub-pane — med mouse-over-forklaringer
- [ ] Benchmark-overlay på alle grafer (vælg S&P500/NASDAQ/Dow at sammenligne mod)
- [ ] **Live Yahoo-charts fravalgt** — vi bruger vores egne data (besluttet). Kan genovervejes hvis vi vil have intradag.
- [ ] Datarensning: håndtér flere korrupte serier (fladlinede spikes, denominerings-skift)
- [ ] Daglig automatik: ingest (top-up) → fetch_benchmarks → precompute via cron
- [ ] `stockOverview_runs`: lad Java-ingesten logge fulde/delvise kørsler (til dashboard-status)
- [ ] Percentil-rang pr. metric (fx "P/E i nederste 10% af sektoren")
- [ ] Likviditetsfiltre (min. pris, min. gns. volumen)
