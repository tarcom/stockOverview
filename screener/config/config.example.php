<?php
// Kopiér til config/config.php og udfyld. config.php er gitignored og må ALDRIG committes.
// Læser de samme stockOverview_*-tabeller som Java-ingesten fylder (i 'stocks'-DB'en på HTPC).
return [
    'db' => [
        'host' => '127.0.0.1',
        'port' => 3306,
        'name' => 'stocks',
        'user' => 'stocks',
        'pass' => 'stocks_dev',
        'charset' => 'utf8mb4',
    ],
    // Tabel-prefix (deler database med stockOverview-ingesten).
    'prefix' => 'stockOverview_',
    // Benchmark-indeks der vises/sammenlignes på grafer (Yahoo-symboler).
    'benchmarks' => ['^GSPC' => 'S&P 500', '^IXIC' => 'NASDAQ', '^DJI' => 'Dow Jones'],
    // Indeks der bruges til relativ-styrke/beta-beregning i precompute.
    'rs_benchmark' => '^GSPC',
];
