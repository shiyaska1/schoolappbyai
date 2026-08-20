<?php
// Message pull (mailbox semantics): GET returns every message in this school's pool that this
// device didn't send itself. DELETE clears the whole pool of "not mine" messages — the app calls
// GET then immediately DELETE, so a message is delivered once per pull cycle.
//
// LIMITATION, stated plainly: this pool is shared by the whole school, not per-recipient. If a
// message is meant for several teachers (or several parents), only the FIRST device to pull it
// after it was sent will receive it — DELETE removes it for everyone else too. That's fine for a
// 1:1 thread (a parent and their child's one class teacher, which is the common case here), but
// not for a true broadcast. If you need every intended recipient to get a copy, ask for the
// per-device delivery-tracking version instead of this simpler delete-on-pull one.
require_once __DIR__ . '/lib.php';

$school = param('school', 'school');
$device = param('device', 'device');
$path = messages_dir() . "/$school.json";
$pool = read_json_file($path, []);

$mine = array_values(array_filter($pool, function ($m) use ($device) { return ($m['originDevice'] ?? '') === $device; }));
$forMe = array_values(array_filter($pool, function ($m) use ($device) { return ($m['originDevice'] ?? '') !== $device; }));

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    if (empty($forMe)) send_json([]);
    send_json($forMe);
} elseif ($_SERVER['REQUEST_METHOD'] === 'DELETE') {
    // Keep only this device's own outgoing messages (so a re-push doesn't duplicate); drop the rest.
    write_json_file($path, $mine);
    send_json(['ok' => true, 'cleared' => count($forMe)]);
} else {
    send_json(['error' => 'GET or DELETE only'], 405);
}
