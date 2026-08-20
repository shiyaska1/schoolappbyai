# School App server

Four small, dependency-free PHP scripts implementing the contract the app's client code
(`CloudSyncManager` and `MessageSync`) expects. Flat JSON-file storage — no database to set up.

## Deploy

1. Upload this whole `server/` folder to any PHP web host (shared hosting is fine — no special
   modules needed beyond stock PHP with `json` support, which is virtually always on).
2. Make sure the web server can create/write `server/data/` and `server/messages/` — most hosts
   allow this by default; if not, `chmod 775` the `server/` folder once after upload.
3. In the app: **Settings → Base URL**, enter the folder's URL, e.g. `https://yourdomain.com/server`.
   Leave the four filename fields as their defaults (`push.php`, `pull.php`, `message_push.php`,
   `message_pull.php`) unless you renamed the files on the server to match something else.

## What each script does

- **push.php** — a device POSTs its courses/divisions/subjects/teachers/students/attendance/
  holidays as JSON. Merged into `data/{school}.json` (unioned by name/roll-number, not overwritten),
  so multiple devices contributing to one school don't erase each other's work.
- **pull.php** — GET returns that school's current merged dataset. 404 if nothing's been pushed yet.
- **message_push.php** — a device POSTs new outgoing messages (JSON array) into a shared per-school pool.
- **message_pull.php** — GET returns messages not sent by the requesting device; DELETE clears them.
  See the limitation noted at the top of that file: this is delete-on-first-pull, not
  per-recipient delivery, so a message meant for several people is only guaranteed to reach
  whichever one syncs first. Fine for the common case here (one parent, one class teacher); ask if
  you need true multi-recipient delivery instead.

## Multiple schools, one server

Every request carries `?school=<id>&device=<id>` — set a different **School ID** per school in
each school's Settings (Base URL can stay the same, pointing at this one server) and their data
stays in separate files (`data/<school>.json`, etc.).
