<?php
// Shared helpers for the School App server scripts. Flat JSON-file storage — no database setup
// needed, just a writable directory next to these scripts. Deploy the whole server/ folder to your
// web host, point the app's Settings -> Base URL at it, and it's ready.
//
// Deliberately written without PHP 7+ type declarations (scalar param/return types, arrow
// functions elsewhere) so it also runs on older shared-hosting PHP builds.

function data_dir() {
    $dir = __DIR__ . '/data';
    if (!is_dir($dir)) mkdir($dir, 0775, true);
    return $dir;
}

function messages_dir() {
    $dir = __DIR__ . '/messages';
    if (!is_dir($dir)) mkdir($dir, 0775, true);
    return $dir;
}

/** Query params are untrusted — keep only safe filename characters. */
function safe_id($s, $fallback) {
    $s = preg_replace('/[^A-Za-z0-9_-]/', '', (string)$s);
    return $s === '' ? $fallback : $s;
}

function param($name, $fallback = '') {
    $v = isset($_GET[$name]) ? $_GET[$name] : (isset($_POST[$name]) ? $_POST[$name] : $fallback);
    return safe_id($v, $fallback);
}

function read_json_body() {
    $raw = file_get_contents('php://input');
    if ($raw === false || $raw === '') return array();
    $decoded = json_decode($raw, true);
    return is_array($decoded) ? $decoded : array();
}

function send_json($data, $code = 200) {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data);
    exit;
}

/** Read a JSON file with a shared lock; returns $default if missing/unreadable. */
function read_json_file($path, $default) {
    if (!file_exists($path)) return $default;
    $fh = fopen($path, 'r');
    if (!$fh) return $default;
    flock($fh, LOCK_SH);
    $raw = stream_get_contents($fh);
    flock($fh, LOCK_UN);
    fclose($fh);
    $decoded = json_decode($raw, true);
    return $decoded === null ? $default : $decoded;
}

/** Write a JSON file atomically-ish with an exclusive lock. */
function write_json_file($path, $data) {
    $fh = fopen($path, 'c');
    flock($fh, LOCK_EX);
    ftruncate($fh, 0);
    rewind($fh);
    fwrite($fh, json_encode($data));
    flock($fh, LOCK_UN);
    fclose($fh);
}

/** Union two lists of associative arrays by a composite key built from $keyFields, keeping
 * whichever side has the higher updatedAtMillis (missing = 0, so present beats absent). */
function merge_rows($existing, $incoming, $keyFields) {
    $byKey = array();
    foreach ($existing as $row) $byKey[row_key($row, $keyFields)] = $row;
    foreach ($incoming as $row) {
        $key = row_key($row, $keyFields);
        $prev = isset($byKey[$key]) ? $byKey[$key] : null;
        $prevUpdated = $prev && isset($prev['updatedAtMillis']) ? $prev['updatedAtMillis'] : 0;
        $rowUpdated = isset($row['updatedAtMillis']) ? $row['updatedAtMillis'] : 0;
        if ($prev === null || $rowUpdated >= $prevUpdated) {
            $byKey[$key] = $row;
        }
    }
    return array_values($byKey);
}

function row_key($row, $fields) {
    $parts = array();
    foreach ($fields as $f) $parts[] = strtolower(trim((string)(isset($row[$f]) ? $row[$f] : '')));
    return implode('|', $parts);
}
