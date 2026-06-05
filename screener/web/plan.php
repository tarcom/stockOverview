<?php
// Midlertidig plan/status-side (renderer PLAN.md). Fjernes på sigt — slet bare
// denne fil + nav-linket i lib/header.php.
require __DIR__ . '/lib/header.php';
require __DIR__ . '/lib/markdown.php';
$planHtml = is_file(__DIR__ . '/../PLAN.md') ? render_markdown(file_get_contents(__DIR__ . '/../PLAN.md')) : '';
?>
<!doctype html>
<html lang="da">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Plan &amp; status — Nørgaard's Aktie Screener</title>
<link rel="stylesheet" href="assets/style.css?v=<?= filemtime(__DIR__ . '/assets/style.css') ?>">
</head>
<body>
<?php render_header('plan'); ?>
<section class="wrap plan">
  <div class="card"><?= $planHtml ?></div>
</section>
<?php render_footer(); ?>
</body>
</html>
