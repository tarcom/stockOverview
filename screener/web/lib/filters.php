<?php
/**
 * Filter-motoren: central definition af alle filtre (grupper, metrics, type, skala,
 * label, hjælpetekst) + bygning af WHERE-klausuler, tælling, resultater og facetter
 * (histogrammer + min/max + multivalg-muligheder). Læser KUN stockOverview_screener.
 */
require_once __DIR__ . '/db.php';

function flt_windows(): array { return ['1m','3m','6m','1y','2y','3y','5y','10y']; }
function win_label(string $w): string {
    return ['1m'=>'1 måned','3m'=>'3 måneder','6m'=>'6 måneder','1y'=>'1 år',
            '2y'=>'2 år','3y'=>'3 år','5y'=>'5 år','10y'=>'10 år'][$w] ?? $w;
}

/** Ét range-filter. */
function f(string $key, string $label, string $scale, string $fmt, string $info): array {
    return ['key'=>$key,'col'=>$key,'type'=>'range','scale'=>$scale,'fmt'=>$fmt,'label'=>$label,'info'=>$info];
}
/** Ét multivalg-filter. */
function fm(string $key, string $label, string $info): array {
    return ['key'=>$key,'col'=>$key,'type'=>'multi','label'=>$label,'info'=>$info];
}

/** Alle filtre, grupperet (PriceRunner-stil: vigtigste øverst, åbne; resten foldet). */
function flt_groups(): array {
    $perf = [];
    foreach (flt_windows() as $w) $perf[] = f("ret_$w", 'Afkast '.strtoupper($w), 'lin', 'pct', 'Samlet kursafkast over '.win_label($w).'.');

    $growth = [];
    foreach (['1y','2y','3y','5y'] as $w)
        $growth[] = f("quality_$w", 'Kvalitets-score '.strtoupper($w), 'lin', 'num',
            'Stabil + høj vækst: annualiseret eksponentiel vækstrate × R² over '.win_label($w).'. Høj værdi = både stærk OG jævn stigning.');
    foreach (['1y','3y','5y'] as $w)
        $growth[] = f("cagr_$w", 'CAGR '.strtoupper($w), 'lin', 'pct', 'Annualiseret (årligt) afkast over '.win_label($w).'.');

    $risk = [];
    foreach (['1y','3y','5y'] as $w)
        $risk[] = f("trend_r2_$w", 'Trend-stabilitet R² '.strtoupper($w), 'lin', 'num',
            'Hvor jævnt aktien følger sin egen vækstkurve (0-1) over '.win_label($w).'. Høj = rolig "compounder".');
    foreach (['1y','3y'] as $w) $risk[] = f("maxdd_$w", 'Max drawdown '.strtoupper($w), 'lin', 'pct', 'Værste top-til-bund-fald i perioden (negativ tal).');
    foreach (['1y','3y'] as $w) $risk[] = f("sharpe_$w", 'Sharpe '.strtoupper($w), 'lin', 'num', 'Risikojusteret afkast: CAGR ÷ volatilitet.');
    $risk[] = f('vol_1y', 'Volatilitet 1Y', 'lin', 'pct', 'Annualiseret volatilitet (udsving).');

    $mkt = [
        f('beta_1y', 'Beta 1Y', 'lin', 'num', 'Følsomhed over for S&P500. ~1 = følger markedet, >1 = forstærker, <1 = dæmper.'),
        f('mkt_r2_1y', 'Markeds-korrelation² 1Y', 'lin', 'num', 'Hvor stor del af udsvingene der forklares af S&P500 (0-1). Høj = bevæger sig i takt med markedet; lav = idiosynkratisk/tilfældig volatilitet.'),
    ];
    foreach (['1y','3y','5y'] as $w) $mkt[] = f("rs_$w", 'Relativ styrke '.strtoupper($w), 'lin', 'pct', 'Merafkast vs S&P500 over '.win_label($w).'. Positiv = slår markedet.');

    return [
      ['id'=>'market','title'=>'Marked & størrelse','open'=>true,'filters'=>[
        f('mkt_cap_usd','Markedsværdi (USD)','log','usd','Virksomhedens samlede markedsværdi omregnet til USD. Logaritmisk slider.'),
        f('history_years','Historik (år)','lin','num','Antal års kurshistorik i databasen. Slid op for kun veletablerede aktier.'),
        f('max_day_move','Maks. dagsudsving','lin','pct','Største 1-dags kursudsving i hele historikken. Værdier over 100% skyldes oftest datafejl — slid den ned for at luge korrupte serier ud.'),
        f('last_close','Seneste kurs','log','num','Seneste lukkekurs i aktiens egen valuta.'),
      ]],
      ['id'=>'perf','title'=>'Performance pr. periode','open'=>true,'filters'=>$perf],
      ['id'=>'growth','title'=>'Vækst & kvalitet','open'=>true,'filters'=>$growth],
      ['id'=>'risk','title'=>'Stabilitet & risiko','open'=>false,'filters'=>$risk],
      ['id'=>'mktcorr','title'=>'Marked-korrelation','open'=>false,'filters'=>$mkt],
      ['id'=>'value','title'=>'Værdiansættelse','open'=>false,'filters'=>[
        f('trailing_pe','P/E','lin','num','Den almindelige P/E: kurs ÷ indtjening de seneste 12 måneder. Selskaber med negativ eller meningsløs indtjening (P/E ≤ 0 eller > 1000) vises som N/A, ikke som et tal.'),
        f('forward_pe','Forward P/E','lin','num','Kurs ÷ forventet fremtidig indtjening (analytikernes estimat).'),
        f('peg_ratio','PEG','lin','num','P/E ÷ vækst. Under 1 anses ofte som attraktivt.'),
        f('price_to_book','P/B','lin','num','Kurs ÷ bogført egenkapital.'),
        f('price_to_sales','P/S','lin','num','Kurs ÷ omsætning.'),
        f('ev_to_ebitda','EV/EBITDA','lin','num','Virksomhedsværdi ÷ EBITDA.'),
        f('dividend_yield','Udbytte %','lin','pct','Årligt udbytte ÷ kurs.'),
      ]],
      ['id'=>'profit','title'=>'Profitabilitet & sundhed','open'=>false,'filters'=>[
        f('return_on_equity','ROE','lin','pct','Afkast på egenkapital.'),
        f('return_on_assets','ROA','lin','pct','Afkast på aktiver.'),
        f('profit_margins','Nettomargin','lin','pct','Overskud ÷ omsætning.'),
        f('gross_margins','Bruttomargin','lin','pct','Bruttoavance ÷ omsætning.'),
        f('operating_margins','Driftsmargin','lin','pct','Driftsresultat ÷ omsætning.'),
        f('revenue_growth','Omsætningsvækst','lin','pct','Vækst i omsætning (seneste).'),
        f('earnings_growth','Indtjeningsvækst','lin','pct','Vækst i indtjening (seneste).'),
        f('debt_to_equity','Gæld/egenkapital','lin','num','Lavere = mindre gældsat.'),
        f('current_ratio','Current ratio','lin','num','Likviditet: omsætningsaktiver ÷ kortfristet gæld.'),
      ]],
      ['id'=>'class','title'=>'Klassifikation','open'=>false,'filters'=>[
        fm('sector','Sektor','Virksomhedens sektor.'),
        fm('country','Land','Virksomhedens hjemland.'),
      ]],
    ];
}

/** Flad opslagstabel key => filter-def. */
function flt_all(): array {
    static $a = null;
    if ($a === null) { $a = []; foreach (flt_groups() as $g) foreach ($g['filters'] as $f) $a[$f['key']] = $f; }
    return $a;
}

/** Hvilke kolonner må man sortere på (whitelist). */
function flt_sortable(): array {
    $cols = ['mkt_cap_usd','history_years','dividend_yield','trailing_pe','price_to_book','return_on_equity'];
    foreach (['ret','cagr','quality','trend_r2','sharpe','maxdd','vol','beta','mkt_r2','rs'] as $m)
        foreach (flt_windows() as $w) $cols[] = "{$m}_{$w}";
    return $cols;
}

/** Bygger WHERE + bind-værdier ud fra request-parametre. */
function flt_where(array $p): array {
    $where = []; $bind = [];
    foreach (flt_all() as $key => $f) {
        if ($f['type'] === 'range') {
            $min = $p["{$key}_min"] ?? null; $max = $p["{$key}_max"] ?? null;
            if (is_numeric($min)) { $where[] = "$f[col] >= ?"; $bind[] = (float)$min; }
            if (is_numeric($max)) { $where[] = "$f[col] <= ?"; $bind[] = (float)$max; }
        } else { // multi
            $raw = $p[$key] ?? '';
            if (is_string($raw) && $raw !== '') {
                $list = array_values(array_filter(explode('~', $raw), fn($v) => $v !== ''));
                if ($list) {
                    $ph = implode(',', array_fill(0, count($list), '?'));
                    $where[] = "$f[col] IN ($ph)";
                    foreach ($list as $v) $bind[] = $v;
                }
            }
        }
    }
    return [$where ? implode(' AND ', $where) : '1', $bind];
}

function flt_total(): int {
    return (int) db()->query("SELECT COUNT(*) FROM " . t('screener'))->fetchColumn();
}

function flt_count(array $p): int {
    [$w, $b] = flt_where($p);
    $st = db()->prepare("SELECT COUNT(*) FROM " . t('screener') . " WHERE $w");
    $st->execute($b);
    return (int) $st->fetchColumn();
}

/** Funnel: kumulativ tælling efterhånden som hvert AKTIVT filter lægges på. */
function flt_funnel(array $p): array {
    $active = [];
    foreach (flt_all() as $key => $f) {
        if ($f['type'] === 'range') {
            if (is_numeric($p["{$key}_min"] ?? null) || is_numeric($p["{$key}_max"] ?? null)) $active[] = $f;
        } else if (($p[$key] ?? '') !== '') $active[] = $f;
    }
    $steps = [['label' => 'Hele universet', 'n' => flt_total()]];
    $acc = [];
    foreach ($active as $f) {
        $acc[$f['key']] = $f;
        // byg delvist params med kun de hidtidige aktive filtre
        $sub = [];
        foreach ($acc as $k => $af) {
            if ($af['type'] === 'range') { $sub["{$k}_min"] = $p["{$k}_min"] ?? null; $sub["{$k}_max"] = $p["{$k}_max"] ?? null; }
            else $sub[$k] = $p[$k] ?? '';
        }
        $steps[] = ['label' => $f['label'], 'n' => flt_count($sub)];
    }
    return $steps;
}

/** Top-N resultater, sorteret. */
function flt_results(array $p, string $sort, string $dir, int $limit): array {
    [$w, $b] = flt_where($p);
    $sort = in_array($sort, flt_sortable(), true) ? $sort : 'quality_1y';
    $dir = strtoupper($dir) === 'ASC' ? 'ASC' : 'DESC';
    $cols = "symbol,name,sector,country,quote_type,currency,mkt_cap_usd,last_close,history_years,
        quality_1y,quality_3y,cagr_1y,ret_1m,ret_6m,ret_1y,ret_3y,ret_5y,trend_r2_3y,maxdd_1y,
        sharpe_1y,vol_1y,beta_1y,mkt_r2_1y,rs_1y,trailing_pe,price_to_book,dividend_yield,return_on_equity";
    $sql = "SELECT $cols, $sort AS _sortval FROM " . t('screener') . " WHERE ($w) AND $sort IS NOT NULL ORDER BY $sort $dir LIMIT ?";
    $st = db()->prepare($sql);
    $i = 1; foreach ($b as $v) $st->bindValue($i++, $v);
    $st->bindValue($i, $limit, PDO::PARAM_INT);
    $st->execute();
    return $st->fetchAll();
}

/**
 * Facetter: pr. range-filter min/max + histogram (24 buckets, outlier-robust via mean±3σ,
 * log-skala hvor relevant); pr. multivalg distinkte værdier + antal. Beregnes globalt
 * (hele universet) — giver brugeren et fast billede af "hvor data ligger".
 */
/**
 * Sane hard-grænser pr. metric til slider-domænerne. Mange værdier i rådata er
 * fysisk umulige eller korrupte (margin ±20000%, P/S negativ, beta ±32, drawdown
 * <-100%), så vi klipper sliderne til fornuftige intervaller i stedet for at lade
 * outliers gøre dem ubrugelige. Aktier udenfor klumper bare i sliderens ender.
 */
function flt_hard_bounds(): array {
    return [
        'mkt_cap_usd'=>[1e6,3e12], 'last_close'=>[0.01,1e5], 'history_years'=>[0,10.5], 'max_day_move'=>[0,2],
        'ret_1m'=>[-0.6,0.6],'ret_3m'=>[-0.8,1],'ret_6m'=>[-0.9,1.5],'ret_1y'=>[-1,3],
        'ret_2y'=>[-1,5],'ret_3y'=>[-1,8],'ret_5y'=>[-1,15],'ret_10y'=>[-1,30],
        'cagr_1y'=>[-1,3],'cagr_3y'=>[-1,2],'cagr_5y'=>[-1,1],
        'quality_1y'=>[-1,3],'quality_2y'=>[-1,4],'quality_3y'=>[-1,2],'quality_5y'=>[-1,1],
        'trend_r2_1y'=>[0,1],'trend_r2_3y'=>[0,1],'trend_r2_5y'=>[0,1],'mkt_r2_1y'=>[0,1],
        'maxdd_1y'=>[-1,0],'maxdd_3y'=>[-1,0],'sharpe_1y'=>[-3,5],'sharpe_3y'=>[-3,3],'vol_1y'=>[0,1.5],
        'beta_1y'=>[-2,3],'rs_1y'=>[-1,3],'rs_3y'=>[-1,5],'rs_5y'=>[-1,10],
        'trailing_pe'=>[0,100],'forward_pe'=>[0,100],'peg_ratio'=>[0,10],'price_to_book'=>[0,20],
        'price_to_sales'=>[0,30],'ev_to_ebitda'=>[0,50],'dividend_yield'=>[0,0.15],
        'return_on_equity'=>[-1,1],'return_on_assets'=>[-0.5,0.5],'profit_margins'=>[-1,1],
        'gross_margins'=>[0,1],'operating_margins'=>[-1,1],'revenue_growth'=>[-1,2],
        'earnings_growth'=>[-1,2],'debt_to_equity'=>[0,500],'current_ratio'=>[0,10],
    ];
}

function flt_facets(): array {
    $pdo = db(); $tbl = t('screener');
    $HARD = flt_hard_bounds();
    // Udeluk korrupte aktier (mistænkt datakvalitet) fra domæne+histogram, så de ikke forgifter sliderne.
    $clean = "(max_day_move <= 1 OR max_day_move IS NULL)";
    $out = ['ranges' => [], 'options' => []];
    foreach (flt_all() as $key => $f) {
        if ($f['type'] === 'range') {
            $col = $f['col']; $log = ($f['scale'] === 'log');
            $expr = $log ? "LOG10($col)" : $col;
            $cond = "$col IS NOT NULL" . ($log ? " AND $col > 0" : "") . " AND $clean";
            if (isset($HARD[$key])) {
                // sane hard-grænser
                [$rlo, $rhi] = $HARD[$key];
                $lo = $log ? log10(max($rlo, 1e-9)) : $rlo;
                $hi = $log ? log10($rhi) : $rhi;
                $realMn = $rlo; $realMx = $rhi;
            } else {
                // fallback: outlier-klippet mean±3σ
                $s = $pdo->query("SELECT MIN($expr) mn, MAX($expr) mx, AVG($expr) av, STDDEV($expr) sd, COUNT(*) n
                                  FROM $tbl WHERE $cond")->fetch();
                if (!$s || $s['n'] == 0) { $out['ranges'][$key] = null; continue; }
                $mn = (float)$s['mn']; $mx = (float)$s['mx']; $av = (float)$s['av']; $sd = (float)$s['sd'];
                $lo = $sd > 0 ? max($mn, $av - 3*$sd) : $mn;
                $hi = $sd > 0 ? min($mx, $av + 3*$sd) : $mx;
                if ($hi <= $lo) { $hi = $mx; $lo = $mn; }
                if ($hi <= $lo) $hi = $lo + 1;
                $realMn = $log ? pow(10,$mn) : $mn; $realMx = $log ? pow(10,$mx) : $mx;
            }
            $B = 24;
            $st = $pdo->prepare("SELECT LEAST($B-1, GREATEST(0, FLOOR(($expr - ?) / ? * $B))) b, COUNT(*) n
                FROM $tbl WHERE $cond AND $expr BETWEEN ? AND ? GROUP BY b ORDER BY b");
            $st->execute([$lo, ($hi-$lo), $lo, $hi]);
            $counts = array_fill(0, $B, 0);
            foreach ($st as $r) $counts[(int)$r['b']] = (int)$r['n'];
            // konverter bucket-grænser tilbage til rigtige enheder (log → 10^x)
            $edges = [];
            for ($i = 0; $i <= $B; $i++) { $x = $lo + ($hi-$lo)*$i/$B; $edges[] = $log ? pow(10,$x) : $x; }
            // Slider-domæne = de (hard-grænse eller outlier-klippede) edges; $realMn/$realMx er reference.
            $out['ranges'][$key] = ['min'=>$edges[0],'max'=>$edges[$B],'dmin'=>$realMn,'dmax'=>$realMx,
                'scale'=>$f['scale'],'fmt'=>$f['fmt'],'edges'=>$edges,'counts'=>$counts];
        } else {
            $rows = $pdo->query("SELECT COALESCE(NULLIF($f[col],''),'(ukendt)') v, COUNT(*) n
                FROM $tbl GROUP BY v ORDER BY n DESC LIMIT 60")->fetchAll();
            $out['options'][$key] = $rows;
        }
    }
    return $out;
}

// ---------- Grafdata (base-100 tidsserier til udfaldsrum-graferne) ----------

function win_days(string $w): int {
    return ['1m'=>30,'3m'=>91,'6m'=>182,'1y'=>365,'2y'=>730,'3y'=>1095,'5y'=>1826,'10y'=>3652][$w] ?? 1095;
}

/** Reducerer en serie til højst $max punkter (jævn sampling, beholder først+sidst). */
function downsample(array $pts, int $max): array {
    $n = count($pts);
    if ($n <= $max) return $pts;
    $step = $n / $max; $out = [];
    for ($i = 0; $i < $max; $i++) $out[] = $pts[(int)floor($i * $step)];
    $out[] = $pts[$n - 1];
    return $out;
}

/** Indekserer en serie til base-100 (første punkt = 100). */
function base100(array $pts): array {
    if (!$pts) return [];
    $p0 = $pts[0][1];
    if ($p0 == 0) return $pts;
    return array_map(fn($p) => [$p[0], round($p[1] / $p0 * 100, 2)], $pts);
}

/**
 * Base-100 tidsserier for de viste aktier + et benchmark-indeks, over et tidsvindue.
 * Læser rå dagshistorik (kun for de få viste symboler) og downsampler til ~220 punkter.
 */
function flt_chart(array $symbols, string $window, string $bench): array {
    $symbols = array_slice(array_values(array_filter($symbols, fn($s) => $s !== '')), 0, 16);
    $window  = array_key_exists($window, array_flip(flt_windows())) ? $window : '3y';
    $cutoff  = (new DateTime('-' . win_days($window) . ' days'))->format('Y-m-d');
    $out = ['window' => $window, 'series' => [], 'bench' => null];

    if ($symbols) {
        $ph = implode(',', array_fill(0, count($symbols), '?'));
        $st = db()->prepare("SELECT symbol, price_date, COALESCE(adj_close, close) p FROM " . t('prices') . "
            WHERE symbol IN ($ph) AND price_date >= ? AND COALESCE(adj_close, close) IS NOT NULL
            ORDER BY symbol, price_date");
        $st->execute(array_merge($symbols, [$cutoff]));
        $by = [];
        foreach ($st as $r) $by[$r['symbol']][] = [$r['price_date'], (float)$r['p']];
        // bevar inputrækkefølge (sorteret som resultaterne)
        foreach ($symbols as $s) {
            if (empty($by[$s])) continue;
            $out['series'][] = ['symbol' => $s, 'points' => base100(downsample($by[$s], 220))];
        }
    }

    if (array_key_exists($bench, cfg()['benchmarks'])) {
        $st = db()->prepare("SELECT price_date, close FROM " . t('indexes') . "
            WHERE symbol = ? AND price_date >= ? ORDER BY price_date");
        $st->execute([$bench, $cutoff]);
        $bp = [];
        foreach ($st as $r) $bp[] = [$r['price_date'], (float)$r['close']];
        if ($bp) $out['bench'] = ['symbol' => $bench, 'label' => cfg()['benchmarks'][$bench],
                                  'points' => base100(downsample($bp, 220))];
    }
    return $out;
}

/**
 * Daglig serie (fuld opløsning) for ÉN aktie over et vindue + benchmark — til fokus-view
 * med tekniske indikatorer (SMA/RSI/MACD/volumen beregnes klientside af den rå serie).
 */
function flt_stock(string $symbol, string $window): array {
    $window = in_array($window, flt_windows(), true) ? $window : '3y';
    $cutoff = (new DateTime('-' . win_days($window) . ' days'))->format('Y-m-d');
    $st = db()->prepare("SELECT price_date, COALESCE(adj_close, close) p, volume FROM " . t('prices') . "
        WHERE symbol = ? AND price_date >= ? AND COALESCE(adj_close, close) IS NOT NULL ORDER BY price_date");
    $st->execute([$symbol, $cutoff]);
    $pts = [];
    foreach ($st as $r) $pts[] = [$r['price_date'], (float)$r['p'], $r['volume'] !== null ? (int)$r['volume'] : null];

    $meta = db()->prepare("SELECT name, currency, sector, country, exchange, employees, website, business_summary
        FROM " . t('securities') . " WHERE symbol = ?");
    $meta->execute([$symbol]); $m = $meta->fetch() ?: [];

    // Hele screener-rækken (fundamentals + alle metrics) til detalje-panelet.
    $sc = db()->prepare("SELECT * FROM " . t('screener') . " WHERE symbol = ?");
    $sc->execute([$symbol]); $row = $sc->fetch() ?: [];

    // Percentil-rang inden for sektoren for et par nøgletal (fraktion af peers med metric <= denne).
    $pctMetrics = ['trailing_pe','price_to_book','ev_to_ebitda','return_on_equity','profit_margins',
        'dividend_yield','quality_1y','ret_1y','revenue_growth'];
    $pcts = [];
    $sector = $row['sector'] ?? null;
    if ($sector) {
        $sel = []; $bind = [];
        foreach ($pctMetrics as $mt) {
            if (isset($row[$mt]) && $row[$mt] !== null) { $sel[] = "AVG(`$mt` <= ?) `$mt`"; $bind[] = $row[$mt]; }
        }
        if ($sel) {
            $bind[] = $sector;
            $q = db()->prepare("SELECT " . implode(',', $sel) . ", COUNT(*) _n FROM " . t('screener') . " WHERE sector = ?");
            $q->execute($bind);
            $pcts = $q->fetch() ?: [];
        }
    }

    $bench = cfg()['rs_benchmark']; $bp = [];
    $bs = db()->prepare("SELECT price_date, close FROM " . t('indexes') . " WHERE symbol = ? AND price_date >= ? ORDER BY price_date");
    $bs->execute([$bench, $cutoff]);
    foreach ($bs as $r) $bp[] = [$r['price_date'], (float)$r['close']];

    return ['symbol'=>$symbol, 'name'=>$m['name'] ?? $symbol, 'currency'=>$m['currency'] ?? '',
        'sector'=>$m['sector'] ?? '', 'country'=>$m['country'] ?? '', 'exchange'=>$m['exchange'] ?? '',
        'employees'=>$m['employees'] ?? null, 'website'=>$m['website'] ?? '', 'summary'=>$m['business_summary'] ?? '',
        'window'=>$window, 'meta'=>$row, 'percentiles'=>$pcts,
        'points'=>$pts, 'bench'=>['symbol'=>$bench, 'label'=>cfg()['benchmarks'][$bench] ?? $bench, 'points'=>$bp]];
}
