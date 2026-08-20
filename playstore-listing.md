# Play Store Submission Data — School Management App

Copy each section directly into the matching Play Console field.

---

## 1. App identity

| Field | Value |
|---|---|
| Package name (applicationId) | `com.school.attendance` |
| App name | School Management App |
| Category | Education |
| Tags (optional) | School administration, Attendance |
| Contact email | (use the email you want parents/support requests to reach) |
| Website (optional) | (your school's site, if any) |

---

## 2. Short description (max 80 characters)

```
Attendance, fees, accounts, exams and bus tracking for your whole school.
```
(74 characters)

---

## 3. Full description (max 4000 characters)

```
School Management App is a complete, offline-first tool for running a school's daily operations — built for admins, teachers, non-teaching staff, and parents, all in one app.

ATTENDANCE
• Mark student attendance by class/division, once or twice a day
• Staff attendance, including geo-fenced self-attendance for teachers and drivers
• Attendance summaries and reports, exportable as CSV

EXAMS & REPORT CARDS
• Define Unit Tests and Term Exams per class
• Teachers enter marks per subject; automatically scaled to each exam's weight in the final report
• Configurable grade scale (e.g. 90-100 = A+)
• Consolidated, printable/shareable A4 report cards with student photo, marks, and grade

FEES & ACCOUNTING
• Full double-entry accounting: chart of accounts, journal vouchers, receipts, expenses, purchases
• Customers/suppliers, ledgers, and financial reports (day book, trial balance, profit & loss, balance sheet)

BUS TRACKING
• Live bus location on an in-app map for parents and staff
• Drivers share location with one tap, auto-updating every minute
• Approximate arrival time to school

COMMUNICATION
• In-app messaging between school, teachers, and parents
• WhatsApp sharing for login credentials, fee receipts, and reports

MULTI-DEVICE SYNC
• Works fully offline; syncs to your own server when online
• Every device merges cleanly — no data lost between multiple phones
• Manual backup/restore to a local file as well

ROLES
• Admin: full access to masters, transactions, reports, and accounts
• Teacher/staff: attendance, exam marks, messages, reports for their own classes
• Parent: a simple dashboard for their child's attendance, fees, exam results, and bus location — installed and set up via a link shared by the school, no technical setup needed

Built for schools that want a single, affordable, private system — your data stays on your own server, not a third party's.
```
(character count is under the 4000 limit — trim further if you want a shorter listing)

---

## 4. Privacy Policy (host this as a page and put its URL in Play Console)

```
Privacy Policy — School Management App

Last updated: [DATE]

School Management App ("the App") is provided to [YOUR SCHOOL NAME] ("the School") for managing attendance, academic records, fees, and communication between school staff and parents/guardians.

1. Who controls your data
The School is the data controller. The App's developer does not operate any central server — all data is stored on infrastructure the School itself controls (its own web hosting). The developer does not have access to, and does not collect, any school's data.

2. What information is collected
Depending on your role (admin, teacher/staff, or parent), the App may store:
- Student information: name, roll number, admission number, date of birth, gender, address, guardian name and contact details, photo, attendance records, exam marks, and fee/payment records.
- Staff information: name, phone number, designation, salary (for payroll), photo, attendance records, and — only for staff who enable it — precise location, used solely for geo-fenced self-attendance and bus-location sharing.
- Parent/guardian login: a username and password generated for accessing their child's records.
- Device information: a randomly generated device identifier used only to attribute synced data to a specific phone, not to identify a person.

3. How information is used
Data is used only to operate the App's features: attendance tracking, exam and report-card generation, fee/accounting records, bus location sharing, and in-app messaging between school staff and parents.

4. Location data
Location is collected only when a staff member (typically a bus driver) explicitly turns on location sharing, or when a staff member uses geo-fenced self-attendance. Location is used to show the bus's current position to parents/staff and to verify attendance is marked on school premises. Location sharing runs as a visible, ongoing notification while active and can be turned off at any time from the App.

5. Data sharing
Data is not sold or shared with third parties. Data is transmitted only between the App and the School's own server, over the network path the School has configured. Bulk SMS/WhatsApp features, if enabled by the School, send messages to the specific number entered by school staff (e.g. a guardian's phone) and are not used for marketing.

6. Data storage and security
Data is stored locally on each device and synced to the School's own server. The developer strongly recommends the School serve its sync endpoint over HTTPS. Local device data can be erased at any time by uninstalling the App or using its "Complete restore" feature.

7. Data retention and deletion
The School controls how long data is retained on its own server. To request deletion of a specific student's or staff member's data, contact the School directly at [SCHOOL CONTACT EMAIL].

8. Children's data
The App is used by school staff and parents/guardians, not directly by children. Student records are entered and managed by authorized school staff on behalf of the School, which is responsible for obtaining any consent required under applicable law (e.g. from parents/guardians).

9. Your rights
Parents/guardians and staff may contact the School at [SCHOOL CONTACT EMAIL] to review, correct, or request deletion of their own or their child's data.

10. Changes to this policy
This policy may be updated from time to time; the "Last updated" date above will reflect the most recent change.

Contact: [SCHOOL CONTACT EMAIL / PHONE]
```

Replace every `[BRACKETED]` placeholder before publishing. Host it anywhere reachable by a plain URL (a simple page on the school's own website, a GitHub Pages page, a Google Doc published to the web, etc.) and put that URL into Play Console → App content → Privacy policy.

---

## 5. Data safety form (Play Console → App content → Data safety)

**Does your app collect or share any of the required user data types?** Yes

| Data type | Collected? | Shared with third parties? | Purpose |
|---|---|---|---|
| Name | Yes | No | App functionality, account management |
| Phone number | Yes | No | App functionality (contact, login) |
| Email address | Yes (optional field) | No | App functionality |
| Address | Yes | No | App functionality |
| Photos | Yes | No | App functionality (ID photos on records/report cards) |
| Precise location | Yes | No | App functionality (bus tracking, geo-fenced attendance) — only while a staff member has it turned on |
| User IDs | Yes (device ID) | No | App functionality (sync) |
| App activity | No | — | — |
| Financial info | Yes (fee/payment amounts) | No | App functionality |

**Is all user data encrypted in transit?** Yes, if the School's server uses HTTPS (recommended — tell the School to use an `https://` base URL in Settings).

**Do you provide a way for users to request data deletion?** Yes — via the School (see Privacy Policy contact section) and via in-app deletion/uninstall.

**Data collection is required or optional:** Most fields are entered by school staff, not the end user directly signing up — mark as "Collected" rather than "Optional" per field, matching the table above.

---

## 6. Content rating questionnaire (Play Console → App content → Content rating)

Answer as:
- Violence: None
- Sexual content: None
- Profanity: None
- Controlled substances: None
- Gambling: None
- User-generated content / communication: Yes — the app has in-app messaging between school staff and parents (not open/public chat, no public posting)

This should land the app at **Everyone** rating on most rating boards.

---

## 7. Target audience and content

- Target age group: 18 and over (the App is used by adults — school staff and parents/guardians)
- "Is your app designed to be appealing to children?": No
- "Is your app primarily child-directed?": No

Even though the App stores student records, the App itself is operated by adults, not by children — answer accordingly. The School (as data controller) is responsible for any additional compliance (e.g. local student-data-protection laws) — this is normal for school administration software and worth a line in your own terms if you want to be extra safe.

---

## 8. Permissions declaration

The App requests: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

Notably **not** requested: `ACCESS_BACKGROUND_LOCATION` — bus tracking runs as a foreground service with a persistent notification while active, not true background access. This means you should **not** need to fill out Google's separate background-location permission declaration form, which is normally the slowest part of review for a location app.

If Play Console still flags `FOREGROUND_SERVICE_LOCATION` for a permissions declaration form, the justification to give is:
```
Used only while a school bus driver has explicitly turned on "Share my bus location" from the app's dashboard, so parents and staff can see the bus's live position. Shown as a persistent, dismissible notification the whole time it's active.
```

---

## 9. Release track

Start with **Internal testing** (fastest, no review wait) to confirm the signed AAB installs correctly, then move to **Closed testing** with a handful of real staff/parents, then **Production**. Your repo's `release.yml`/`keygen.yml` GitHub Actions already build the signed AAB — you'll need to have run `keygen.yml` once and stored the resulting keystore/secrets before `release.yml` can produce a real upload build.
