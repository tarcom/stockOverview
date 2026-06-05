<?php
/**
 * Minimal Markdown → HTML. Dækker det PLAN.md bruger: overskrifter, fed, inline-kode,
 * kodeblokke, tabeller, task-lister ([ ]/[x]), punktlister, links, afsnit.
 * (Bevidst lille — ingen Composer/afhængigheder, virker også på one.com.)
 */
function render_markdown(string $md): string {
    $lines = preg_split('/\r?\n/', $md);
    $html = [];
    $n = count($lines);
    $i = 0;
    $inUl = false;
    $closeUl = function() use (&$inUl, &$html) { if ($inUl) { $html[] = '</ul>'; $inUl = false; } };

    while ($i < $n) {
        $line = $lines[$i];

        // Kodeblok
        if (preg_match('/^```/', $line)) {
            $closeUl();
            $buf = [];
            $i++;
            while ($i < $n && !preg_match('/^```/', $lines[$i])) { $buf[] = htmlspecialchars($lines[$i]); $i++; }
            $i++;
            $html[] = '<pre><code>' . implode("\n", $buf) . '</code></pre>';
            continue;
        }

        // Tabel (header-linje efterfulgt af |---|)
        if (strpos($line, '|') !== false && isset($lines[$i+1]) && preg_match('/^\s*\|?[\s:|-]+\|?\s*$/', $lines[$i+1]) && strpos($lines[$i+1], '-') !== false) {
            $closeUl();
            $header = md_row($line);
            $i += 2;
            $rows = [];
            while ($i < $n && strpos($lines[$i], '|') !== false && trim($lines[$i]) !== '') {
                $rows[] = md_row($lines[$i]); $i++;
            }
            $t = '<table><thead><tr>';
            foreach ($header as $c) $t .= '<th>' . md_inline($c) . '</th>';
            $t .= '</tr></thead><tbody>';
            foreach ($rows as $r) {
                $t .= '<tr>';
                foreach ($r as $c) $t .= '<td>' . md_inline($c) . '</td>';
                $t .= '</tr>';
            }
            $html[] = $t . '</tbody></table>';
            continue;
        }

        // Overskrifter
        if (preg_match('/^(#{1,4})\s+(.*)$/', $line, $m)) {
            $closeUl();
            $lvl = strlen($m[1]);
            $html[] = "<h$lvl>" . md_inline($m[2]) . "</h$lvl>";
            $i++; continue;
        }

        // Task-/punktliste
        if (preg_match('/^\s*[-*]\s+(.*)$/', $line, $m)) {
            if (!$inUl) { $html[] = '<ul>'; $inUl = true; }
            $item = $m[1];
            if (preg_match('/^\[([ xX])\]\s+(.*)$/', $item, $t)) {
                $checked = strtolower($t[1]) === 'x';
                $box = $checked ? '☑' : '☐';
                $cls = $checked ? ' class="done"' : '';
                $html[] = "<li$cls><span class=\"chk\">$box</span> " . md_inline($t[2]) . '</li>';
            } else {
                $html[] = '<li>' . md_inline($item) . '</li>';
            }
            $i++; continue;
        }

        // Tom linje
        if (trim($line) === '') { $closeUl(); $i++; continue; }

        // Afsnit
        $closeUl();
        $html[] = '<p>' . md_inline($line) . '</p>';
        $i++;
    }
    $closeUl();
    return implode("\n", $html);
}

function md_row(string $line): array {
    $line = trim($line);
    $line = preg_replace('/^\||\|$/', '', $line);
    return array_map('trim', explode('|', $line));
}

function md_inline(string $s): string {
    $s = htmlspecialchars($s, ENT_QUOTES);
    $s = preg_replace('/`([^`]+)`/', '<code>$1</code>', $s);
    $s = preg_replace('/\*\*([^*]+)\*\*/', '<strong>$1</strong>', $s);
    $s = preg_replace('/\[([^\]]+)\]\(([^)]+)\)/', '<a href="$2">$1</a>', $s);
    return $s;
}
