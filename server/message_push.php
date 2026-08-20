<?php
// Message push: a device POSTs a JSON array of new outgoing messages. Appended to a shared
// per-school pool, each tagged with the sending device id (so that device doesn't get its own
// message echoed back on its next pull) and a server-assigned id.
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') send_json(array('error' => 'POST only'), 405);

$school = param('school', 'school');
$device = param('device', 'device');
$incoming = read_json_body();
if (!is_array($incoming)) send_json(array('error' => 'expected a JSON array'), 400);

$path = messages_dir() . "/$school.json";
$pool = read_json_file($path, array());
$nextId = 1;
foreach ($pool as $m) $nextId = max($nextId, (isset($m['id']) ? $m['id'] : 0) + 1);

foreach ($incoming as $m) {
    $m['id'] = $nextId++;
    $m['originDevice'] = $device;
    $pool[] = $m;
}

write_json_file($path, $pool);
send_json(array('ok' => true, 'accepted' => count($incoming)));
