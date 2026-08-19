# School Attendance App

Android app (Kotlin + Jetpack Compose, offline-first with server sync) for teachers and admins to
manage attendance. Same architecture as the POS billing app in `android-app` — Room for local
storage, a generic URL-based push/pull sync, a provider-agnostic bulk SMS gateway.

## Feature set

- **Masters**: Course → Division → Subject, each subject assigned to a teacher.
- **Teachers & staff**: teaching and non-teaching staff, admin flag, login PIN, monthly salary.
- **Students**: roll number, division, guardian phone + WhatsApp number.
- **Attendance**: once-a-day or morning/afternoon (toggle in Settings), holiday-aware.
- **Staff attendance**: admin marks teacher/staff present-absent separately from students.
- **Holidays**: weekly off days (default Sat+Sun, editable), one-tap fetch of Indian public
  holidays (via the free [Nager.Date](https://date.nager.at) API, no key needed), manual holidays
  scoped to one division or the whole school.
- **Reports**: date range (defaults to 1st-of-month → today), per-student or whole-division,
  working/present/absent/%, CSV export, one-by-one WhatsApp share to guardians.
- **Payroll**: per-teacher payable amount from monthly salary ÷ working days × present days, CSV export.
- **Sync**: push/pull JSON to your own server, keyed by an auto-generated per-install device ID
  plus an optional school ID (for a server hosting multiple schools). Manual "Sync now" or
  auto-sync on an interval.
- **Login**: pick your name, PIN if set. First run creates the initial admin account. Admins can
  switch between the admin dashboard and the plain teacher dashboard.

## What still needs a server

Push/pull sync (`Settings → Push/pull sync`) expects **your own backend** at the URLs you enter:
- `GET <pullUrl>?school=..&device=..` → returns this school's JSON (same shape as the export; see
  `AttendanceSync.exportJson`), or 404 if nothing has been pushed for that school/device yet.
- `POST <pushUrl>?school=..&device=..` with the JSON body → stores it, keyed by `school`+`device`.

No such server is included here — this app only has the client side. Same shape for the SMS
gateway: any provider that accepts a URL template (`{number}` `{message}` `{apikey}` `{sender}`)
works, configured in Settings.

## Known simplifications (flagged honestly, not hidden)

- **CSV, not `.xlsx`**: "export to Excel" is CSV — every spreadsheet app opens it, and it needed no
  extra library (a real `.xlsx` would mean adding Apache POI, a heavy dependency for a first cut).
- **No compile verification**: this environment has no Android SDK / Gradle wrapper installed, so
  the code has not been built. Open the project in Android Studio (it will offer to generate the
  Gradle wrapper on first sync) and run a build before relying on it.
- **Sync merge is name/roll-number keyed**, not ID-keyed (local autoincrement IDs can't line up
  across independent devices) — duplicate names in the same division without roll numbers could
  merge into one record. Add roll numbers to avoid this.
- **WhatsApp share opens the compose screen per student**, one at a time — WhatsApp doesn't allow
  silently auto-sending messages from a third-party app, so the teacher taps Send each time (this
  matches how it was described: "share ... one by one").

## Structure

```
app/src/main/java/com/school/attendance/
  data/       Room entities, DAOs, Repository, AppPrefs (settings), SyncData (JSON export/import)
  sync/       CloudSyncManager (push/pull cycle)
  sms/        SmsSender (generic HTTP gateway)
  util/       CsvExport, WhatsAppShare, DatePicker
  ui/screens/ one file per screen
  ui/theme/   Compose Material3 theme
```
