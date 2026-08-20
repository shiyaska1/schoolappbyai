<?php
// Data pull: returns the school's current merged dataset (built up by push.php across every
// device that has pushed). 404 if this school has never pushed anything yet.
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') send_json(['error' => 'GET only'], 405);

$school = param('school', 'school');
$path = data_dir() . "/$school.json";
if (!file_exists($path)) send_json(['error' => 'no data yet for this school'], 404);

$data = read_json_file($path, null);
if ($data === null) send_json(['error' => 'stored data is corrupt'], 500);
send_json($data);
