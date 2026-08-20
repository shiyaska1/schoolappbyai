<?php
// Data push: a device POSTs its current dataset (masters, attendance, accounting, etc.) as JSON.
// Merged (not overwritten) into data/{school}.json, unioned by natural key (name, roll number,
// voucher number, etc.) so two devices contributing to the same school don't stomp each other.
//
// Deliberately written without PHP 7+ syntax (?? operator, arrow functions, scalar type hints)
// so it also runs on older shared-hosting PHP builds — see lib.php for the same policy.
require_once __DIR__ . '/lib.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') send_json(array('error' => 'POST only'), 405);

$school = param('school', 'school');
$incoming = read_json_body();
if (empty($incoming)) send_json(array('error' => 'empty or invalid JSON body'), 400);

function arr($v, $key) {
    return isset($v[$key]) && is_array($v[$key]) ? $v[$key] : array();
}

$defaults = array(
    'courses' => array(), 'divisions' => array(), 'subjects' => array(), 'buses' => array(),
    'teachers' => array(), 'students' => array(), 'attendance' => array(), 'teacherAttendance' => array(),
    'holidays' => array(),
    'accountGroups' => array(), 'accountHeads' => array(), 'costCenters' => array(),
    'journalEntries' => array(), 'receipts' => array(), 'expenses' => array(),
    'customers' => array(), 'suppliers' => array(), 'purchases' => array(),
);

$path = data_dir() . "/$school.json";
$existing = read_json_file($path, $defaults);

$merged = array(
    'courses' => merge_rows(arr($existing, 'courses'), arr($incoming, 'courses'), array('name')),
    'divisions' => merge_rows(arr($existing, 'divisions'), arr($incoming, 'divisions'), array('name', 'courseName')),
    'subjects' => merge_rows(arr($existing, 'subjects'), arr($incoming, 'subjects'), array('name', 'divisionName')),
    'buses' => merge_rows(arr($existing, 'buses'), arr($incoming, 'buses'), array('busNumber')),
    'teachers' => merge_rows(arr($existing, 'teachers'), arr($incoming, 'teachers'), array('phone')),
    'students' => merge_rows(arr($existing, 'students'), arr($incoming, 'students'), array('rollNumber', 'divisionName')),
    'attendance' => merge_rows(arr($existing, 'attendance'), arr($incoming, 'attendance'), array('studentRoll', 'divisionName', 'dateMillis', 'session')),
    'teacherAttendance' => merge_rows(arr($existing, 'teacherAttendance'), arr($incoming, 'teacherAttendance'), array('teacherPhone', 'dateMillis')),
    'holidays' => merge_rows(arr($existing, 'holidays'), arr($incoming, 'holidays'), array('dateMillis', 'divisionName', 'name')),
    'accountGroups' => merge_rows(arr($existing, 'accountGroups'), arr($incoming, 'accountGroups'), array('name')),
    'accountHeads' => merge_rows(arr($existing, 'accountHeads'), arr($incoming, 'accountHeads'), array('name', 'groupName')),
    'costCenters' => merge_rows(arr($existing, 'costCenters'), arr($incoming, 'costCenters'), array('name')),
    'journalEntries' => merge_rows(arr($existing, 'journalEntries'), arr($incoming, 'journalEntries'), array('voucherNo', 'dateMillis')),
    'receipts' => merge_rows(arr($existing, 'receipts'), arr($incoming, 'receipts'), array('receiptNo')),
    'expenses' => merge_rows(arr($existing, 'expenses'), arr($incoming, 'expenses'), array('voucherNo')),
    'customers' => merge_rows(arr($existing, 'customers'), arr($incoming, 'customers'), array('name', 'phone')),
    'suppliers' => merge_rows(arr($existing, 'suppliers'), arr($incoming, 'suppliers'), array('name', 'phone')),
    'purchases' => merge_rows(arr($existing, 'purchases'), arr($incoming, 'purchases'), array('purchaseNo')),
);

write_json_file($path, $merged);
send_json(array('ok' => true));
