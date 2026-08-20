<?php
// Live location pull. Two modes, query param mode=latest|history (default latest):
// - latest: returns just this person's newest point — read-only, nothing deleted. This is what
//   a parent sees (current position, last-updated time, speed) — no route/history exposed to them.
// - history: returns this person's whole route for the day, THEN clears it server-side — this
//   keeps the (deliberately small/cheap) server from accumulating data nobody asked to keep.
//   Meant for admin/teacher use only; the app itself is what enforces who can request it.
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') send_json(['error' => 'GET only'], 405);

$school = param('school', 'school');
$bus = safe_id($_GET['bus'] ?? '', '');
$mode = ($_GET['mode'] ?? 'latest') === 'history' ? 'history' : 'latest';
if ($bus === '') send_json(['error' => 'bus required'], 400);

$file = __DIR__ . "/locations/$school.json";
$all = read_json_file($file, []);
$route = $all[$bus] ?? [];
if (empty($route)) send_json(['error' => 'no location for this bus yet'], 404);

if ($mode === 'latest') {
    send_json(end($route));
} else {
    $all[$bus] = [];
    write_json_file($file, $all);
    send_json($route);
}
