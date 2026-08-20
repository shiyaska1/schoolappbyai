<?php
// Data push: a device POSTs its current courses/divisions/subjects/teachers/students/attendance/
// holidays as JSON. Merged (not overwritten) into data/{school}.json, unioned by natural key
// (name, roll number, etc.) so two devices contributing to the same school don't stomp each other.
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') send_json(['error' => 'POST only'], 405);

$school = param('school', 'school');
$incoming = read_json_body();
if (empty($incoming)) send_json(['error' => 'empty or invalid JSON body'], 400);

$path = data_dir() . "/$school.json";
$existing = read_json_file($path, [
    'courses' => [], 'divisions' => [], 'subjects' => [], 'teachers' => [],
    'students' => [], 'attendance' => [], 'holidays' => []
]);

$merged = [
    'courses' => merge_rows($existing['courses'] ?? [], $incoming['courses'] ?? [], ['name']),
    'divisions' => merge_rows($existing['divisions'] ?? [], $incoming['divisions'] ?? [], ['name', 'courseName']),
    'subjects' => merge_rows($existing['subjects'] ?? [], $incoming['subjects'] ?? [], ['name', 'divisionName']),
    'teachers' => merge_rows($existing['teachers'] ?? [], $incoming['teachers'] ?? [], ['phone']),
    'students' => merge_rows($existing['students'] ?? [], $incoming['students'] ?? [], ['rollNumber', 'divisionName']),
    'attendance' => merge_rows($existing['attendance'] ?? [], $incoming['attendance'] ?? [], ['studentRoll', 'divisionName', 'dateMillis', 'session']),
    'holidays' => merge_rows($existing['holidays'] ?? [], $incoming['holidays'] ?? [], ['dateMillis', 'divisionName', 'name']),
];

write_json_file($path, $merged);
send_json(['ok' => true]);
