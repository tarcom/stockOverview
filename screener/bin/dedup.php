<?php
/**
 * Dedup: markér krydsnoteringer (samme selskab på flere børser) så screeneren kun
 * viser hvert selskab én gang som standard.
 *
 * Problemet: ~40k af ~76k rækker er sekundær-noteringer — fx Western Digital som WDC
 * (Nasdaq), WDC.F (Frankfurt), WDC.MX (Mexico), WDC.VI (Wien). Samme selskab, lidt
 * forskellige tal (anden valuta/likviditet/handelsdage).
 *
 * Løsning: gruppér på (navn, land) og vælg ét PRIMÆRT listing pr. gruppe ud fra et
 * børs-hierarki (US → Norden/Vesteuropa → Canada/AU → Asien → regionale/OTC), med
 * længst historik og største markedsværdi som tie-break. Sætter:
 *   is_primary     TINYINT  – 1 for det valgte listing, 0 for resten
 *   primary_symbol VARCHAR  – symbolet på gruppens primære listing
 *   listing_count  INT      – antal noteringer i gruppen
 *
 * Screeneren filtrerer is_primary=1 som standard (slå "Vis alle børsnoteringer" til
 * for at se dem alle). Kør efter precompute (kaldes automatisk derfra).
 */
require __DIR__ . '/../web/lib/db.php';

/** Børs-tier: højere = foretrækkes som primært listing. Følger ønsket hierarki. */
function exchange_tier(string $ex): int {
    static $map = null;
    if ($map === null) {
        $tiers = [
            7 => ['NMS','NGM','NCM','NYQ','NYS','ASE','PCX','NAS','BTS','AQS','CXE','CXA'],      // US-børser
            6 => ['LSE','GER','ENX','PAR','AMS','BRU','LIS','EBS','MIL','MCE','ISE','ATH','VIE', // UK + Vesteuropa
                  'STO','HEL','CPH','OSL','ICE','NGM2'],                                          // + Norden
            5 => ['TOR','VAN','CNQ','NEO','ASX','NZE'],                                           // Canada / AU / NZ
            4 => ['JPX','HKG','KSC','KOE','SES','TAI','TWO'],                                     // udviklet Asien
            3 => ['SHH','SHZ','BSE','NSI','KLS','JKT','SET','IST','TLV','SAU','DOH','CAI','JNB',  // EM Asien/MØ/EU
                  'MCX','BUD','PRA','WSE','RIS','TAL','LIT','SAP','BVB','TLO','OEM','OID'],
            2 => ['SAO','BUE','SGO','MEX','CCS',                                                  // LatAm
                  'FRA','STU','MUN','DUS','HAM','HAN','BER','FKA','IOB'],                          // tyske regionale + IOB
            1 => ['PNK','OQB','OQX'],                                                             // OTC/Pink
        ];
        $map = [];
        foreach ($tiers as $tier => $codes) foreach ($codes as $c) $map[$c] = $tier;
    }
    return $map[$ex] ?? 0;
}

$pdo = db();
$tbl = t('screener');

// 1) Sørg for kolonner (MariaDB: IF NOT EXISTS).
foreach ([
    "ADD COLUMN IF NOT EXISTS is_primary TINYINT NOT NULL DEFAULT 1",
    "ADD COLUMN IF NOT EXISTS primary_symbol VARCHAR(32) NULL",
    "ADD COLUMN IF NOT EXISTS listing_count INT NOT NULL DEFAULT 1",
] as $alter) {
    try { $pdo->exec("ALTER TABLE $tbl $alter"); } catch (Throwable $e) { /* findes allerede */ }
}
try { $pdo->exec("CREATE INDEX IF NOT EXISTS idx_${tbl}_primary ON $tbl (is_primary)"); } catch (Throwable $e) {}

// 2) Hent minimal data og gruppér på (navn, land).
$t0 = microtime(true);
$rows = $pdo->query("SELECT symbol, name, country, exchange, history_days, mkt_cap_usd FROM $tbl")->fetchAll();

// Mange sekundær-noteringer har tomt country → udfyld med navnets hyppigste ikke-tomme
// land, så fx alle Nokia-noteringer (nogle "Finland", nogle "") havner i samme gruppe.
$countryVotes = [];
foreach ($rows as $r) {
    $name = strtolower(trim((string)$r['name'])); $c = trim((string)$r['country']);
    if ($name !== '' && $c !== '') $countryVotes[$name][$c] = ($countryVotes[$name][$c] ?? 0) + 1;
}
$modalCountry = [];
foreach ($countryVotes as $name => $votes) { arsort($votes); $modalCountry[$name] = array_key_first($votes); }

$groups = [];
foreach ($rows as $r) {
    $name = strtolower(trim((string)$r['name']));
    if ($name === '') { $groups["\0" . $r['symbol']][] = $r; continue; }   // navnløse kan ikke dedupes
    $country = trim((string)$r['country']);
    if ($country === '') $country = $modalCountry[$name] ?? '';
    $groups[$name . '|' . strtolower($country)][] = $r;
}

// 3) Vælg primært listing pr. gruppe og saml opdateringer.
$updates = []; // symbol => [is_primary, primary_symbol, listing_count]
foreach ($groups as $g) {
    usort($g, function ($a, $b) {
        $ta = exchange_tier((string)$a['exchange']); $tb = exchange_tier((string)$b['exchange']);
        if ($ta !== $tb) return $tb <=> $ta;                                   // højeste tier
        if ($a['history_days'] != $b['history_days']) return $b['history_days'] <=> $a['history_days']; // længst historik
        if ($a['mkt_cap_usd'] != $b['mkt_cap_usd']) return ($b['mkt_cap_usd'] ?? 0) <=> ($a['mkt_cap_usd'] ?? 0);
        return strcmp((string)$a['symbol'], (string)$b['symbol']);             // deterministisk
    });
    $primary = $g[0]['symbol']; $cnt = count($g);
    foreach ($g as $i => $r) $updates[$r['symbol']] = [$i === 0 ? 1 : 0, $primary, $cnt];
}

// 4) Skriv i én transaktion.
$pdo->beginTransaction();
$st = $pdo->prepare("UPDATE $tbl SET is_primary=?, primary_symbol=?, listing_count=? WHERE symbol=?");
$dupes = 0;
foreach ($updates as $sym => [$ip, $ps, $cnt]) { $st->execute([$ip, $ps, $cnt, $sym]); if (!$ip) $dupes++; }
$pdo->commit();

$primaries = count($updates) - $dupes;
printf("Dedup: %d rækker → %d primære selskaber, %d sekundære noteringer skjult (%.1fs).\n",
    count($updates), $primaries, $dupes, microtime(true) - $t0);
