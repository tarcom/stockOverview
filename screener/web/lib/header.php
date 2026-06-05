<?php
/** Fælles topbar/brand for alle sider. Kald render_header('universe'|'screener'). */
function render_header(string $active): void { ?>
<header class="topbar">
  <a class="brand" href="index.php">
    <svg class="logo" viewBox="0 0 32 32" width="30" height="30" aria-hidden="true">
      <defs><linearGradient id="lg" x1="0" y1="1" x2="1" y2="0">
        <stop offset="0" stop-color="#2ec4b6"/><stop offset="1" stop-color="#5b8def"/></linearGradient></defs>
      <polyline points="3,26 12,17 18,21 23,11 29,5" fill="none" stroke="url(#lg)"
        stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="29" cy="5" r="2.8" fill="#5b8def"/>
    </svg>
    <span class="brand-name">Nørgaard's <b>Aktie&nbsp;Screener</b></span>
  </a>
  <nav>
    <a href="index.php"<?= $active === 'universe' ? ' class="active"' : '' ?>>Universet</a>
    <a href="screener.php"<?= $active === 'screener' ? ' class="active"' : '' ?>>Screener</a>
    <a href="index.php#plan">Plan &amp; status</a>
  </nav>
</header>
<?php }
