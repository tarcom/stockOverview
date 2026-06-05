<?php
/**
 * Minimal Yahoo Finance-klient i PHP (cookie + crumb + v8/chart).
 * Bruges KUN til de få benchmark-indeks + FX-par — ikke til de 150k aktier
 * (dem fylder Java-ingesten). Samme gotcha som Java-klienten: brug den generiske
 * "Mozilla/5.0"-UA (en fuld Chrome-streng giver 429 på getcrumb).
 */
class Yahoo {
    private $cookie;
    private $crumb;
    private const UA = 'Mozilla/5.0';

    private function auth(): void {
        if ($this->crumb) return;
        // 1) cookie fra fc.yahoo.com (følg ikke redirects)
        $ch = curl_init('https://fc.yahoo.com');
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true, CURLOPT_HEADER => true, CURLOPT_NOBODY => false,
            CURLOPT_FOLLOWLOCATION => false, CURLOPT_USERAGENT => self::UA, CURLOPT_TIMEOUT => 15,
        ]);
        $resp = curl_exec($ch);
        $hdrSize = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
        $headers = substr($resp, 0, $hdrSize);
        curl_close($ch);
        $cookies = [];
        foreach (explode("\r\n", $headers) as $line) {
            if (stripos($line, 'Set-Cookie:') === 0) {
                $pair = trim(substr($line, 11));
                $cookies[] = explode(';', $pair)[0];
            }
        }
        $this->cookie = implode('; ', $cookies);
        // 2) crumb
        $ch = curl_init('https://query1.finance.yahoo.com/v1/test/getcrumb');
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true, CURLOPT_USERAGENT => self::UA,
            CURLOPT_HTTPHEADER => ['Cookie: ' . $this->cookie], CURLOPT_TIMEOUT => 15,
        ]);
        $this->crumb = trim((string)curl_exec($ch));
        curl_close($ch);
        if ($this->crumb === '') throw new RuntimeException('Kunne ikke hente Yahoo-crumb');
    }

    /** Daglig historik: returnerer [['date'=>'Y-m-d','close'=>float], ...] ældste->nyeste. */
    public function dailyCloses(string $symbol, string $range = '10y'): array {
        $this->auth();
        $url = 'https://query1.finance.yahoo.com/v8/finance/chart/' . rawurlencode($symbol)
             . '?range=' . urlencode($range) . '&interval=1d';
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_RETURNTRANSFER => true, CURLOPT_USERAGENT => self::UA,
            CURLOPT_HTTPHEADER => ['Cookie: ' . $this->cookie], CURLOPT_TIMEOUT => 30,
        ]);
        $body = curl_exec($ch);
        $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        if ($code !== 200) throw new RuntimeException("HTTP $code for $symbol");
        $j = json_decode($body, true);
        $res = $j['chart']['result'][0] ?? null;
        if (!$res || empty($res['timestamp'])) return [];
        $ts = $res['timestamp'];
        $closes = $res['indicators']['quote'][0]['close'] ?? [];
        $out = [];
        foreach ($ts as $i => $t) {
            $c = $closes[$i] ?? null;
            if ($c === null) continue;
            $out[] = ['date' => gmdate('Y-m-d', $t), 'close' => (float)$c];
        }
        return $out;
    }
}
