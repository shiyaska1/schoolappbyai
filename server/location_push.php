<?php
// Live location push: a geo-tracked driver's phone POSTs a batch of queued points
// {teacherPhone, points:[{lat,lng,speedMps,dateMillis}, ...]} — batched because the phone saves
// points locally first and only flushes when it has a connection, so one push can carry several
// minutes' worth caught up at once. Points are appended (not overwritten), building up that
// driver's route for the day until an admin fetches the history (see location_pull.php).
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') send_json(array('error' => 'POST only'), 405);

$school = param('school', 'school');
$body = read_json_body();
$bus = safe_id(isset($body['busNumber']) ? $body['busNumber'] : '', '');
$points = isset($body['points']) ? $body['points'] : array();
if ($bus === '' || !is_array($points) || empty($points)) send_json(array('error' => 'busNumber and points[] required'), 400);

$dir = __DIR__ . '/locations';
if (!is_dir($dir)) mkdir($dir, 0775, true);
$file = "$dir/$school.json";

$all = read_json_file($file, array());
if (!isset($all[$bus]) || !is_array($all[$bus])) $all[$bus] = array();
foreach ($points as $pt) {
    $all[$bus][] = array(
        'lat' => (float)(isset($pt['lat']) ? $pt['lat'] : 0), 'lng' => (float)(isset($pt['lng']) ? $pt['lng'] : 0),
        'speedMps' => (float)(isset($pt['speedMps']) ? $pt['speedMps'] : 0),
        'dateMillis' => (int)(isset($pt['dateMillis']) ? $pt['dateMillis'] : (microtime(true) * 1000)),
    );
}
// Weak/small-storage hosting: never let a route grow unbounded just because nobody fetched the
// history — drop anything older than 1 day regardless of whether an admin ever asked for it.
$cutoff = (microtime(true) - 1 * 24 * 60 * 60) * 1000;
foreach ($all as $p => $route) {
    $all[$p] = array_values(array_filter($route, function ($pt) use ($cutoff) { return (isset($pt['dateMillis']) ? $pt['dateMillis'] : 0) >= $cutoff; }));
}
write_json_file($file, $all);
send_json(array('ok' => true, 'accepted' => count($points)));
