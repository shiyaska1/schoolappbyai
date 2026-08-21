<?php
// Live location pull. Two modes, query param mode=latest|history (default latest):
// - latest: just this bus's newest point.
// - history: this bus's whole route for the day.
// Both are read-only — nothing is deleted here. Storage is capped by location_push.php instead,
// which already drops any point older than 3 days on every push, so "history" and "latest" can
// safely share the same stored array without one view destroying data the other needs.
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') send_json(array('error' => 'GET only'), 405);

$school = param('school', 'school');
$bus = safe_id(isset($_GET['bus']) ? $_GET['bus'] : '', '');
$mode = (isset($_GET['mode']) ? $_GET['mode'] : 'latest') === 'history' ? 'history' : 'latest';
if ($bus === '') send_json(array('error' => 'bus required'), 400);

$file = __DIR__ . "/locations/$school.json";
$all = read_json_file($file, array());
$route = isset($all[$bus]) ? $all[$bus] : array();
if (empty($route)) send_json(array('error' => 'no location for this bus yet'), 404);

if ($mode === 'latest') {
    send_json(end($route));
} else {
    send_json($route);
}
