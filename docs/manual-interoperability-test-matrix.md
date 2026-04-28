# Manual Interoperability Test Matrix

Use this checklist before release to verify Android jtxBoard and the desktop app can safely share the same CalDAV account.

## Test Setup

- Use one test CalDAV account that can be reset safely.
- Install the current Android jtxBoard release on one device or emulator.
- Run the current desktop app against the same account and collection.
- Start each run from a clean test collection unless the scenario explicitly requires existing data.

## Matrix

| Area | Steps | Expected Result |
|---|---|---|
| Collection discovery | Configure desktop credentials and run Discover Collections. | Supported journal/note/task collections appear with display name, color, read-only state, ctag, and sync token where provided. |
| Android journal to desktop | Create a journal in Android with title, multiline description, categories, color, location, comment, attachment reference, and related entry. Sync Android, then sync desktop. | Desktop shows the journal with all supported fields intact and no unknown Android properties dropped after a desktop resync. |
| Desktop journal to Android | Create and edit a desktop journal with markdown/plain text, dates, categories, color, location, comments, attachments, and related entries. Sync desktop, then Android. | Android receives the journal and keeps fields it supports; desktop-only state does not appear as broken iCalendar data. |
| Android note to desktop | Create an Android note stored as `VJOURNAL` without `DTSTART`. Sync both clients. | Desktop imports it as a note, not a journal, and preserves description, categories, comments, attachments, and unknown properties. |
| Desktop note to Android | Create a desktop note and sync. | Exported object is compatible with Android jtxBoard note handling and remains a note on round trip. |
| Android task to desktop | Create a task with due/start dates, priority, progress, recurrence, recurrence exceptions, reminders, categories, color, comments, attachments, and relationships. Sync both clients. | Desktop imports task fields, preserves unsupported recurrence/alarm lines as unknown properties, and does not corrupt the task after saving. |
| Desktop task to Android | Create and edit a desktop task with completion, priority, recurrence, reminders, comments, attachments, and relationships. Sync both clients. | Android receives the task and a subsequent Android sync does not remove desktop-preserved fields. |
| Local create upload | Create one journal, note, and task on desktop while online. Sync desktop, then Android. | All three objects upload once, receive metadata href/etag, and appear on Android. |
| Local update upload | Edit previously synced desktop entries. Sync desktop, then Android. | Android sees updated titles/descriptions/fields; desktop dirty queue clears after successful PUT. |
| Local delete upload | Archive/delete a previously synced desktop entry and sync. | Remote object is deleted or tombstoned according to current desktop behavior; Android no longer shows it after sync. |
| Remote delete detection | Delete an entry on Android and sync Android first. Sync desktop. | Desktop archives/tombstones the matching local entry without creating a dirty local re-upload. |
| Conflict detection | Edit the same synced entry on Android and desktop before either client sees the other change. Sync Android, then desktop. | Desktop reports a conflict and offers Keep Local, Keep Server, and Keep Both; no local data is silently overwritten. |
| Read-only collection | Point desktop at a read-only collection with existing remote objects. | Desktop downloads remote entries but keeps local changes queued and reports read-only state instead of attempting PUT/DELETE. |
| Offline queue | Disable network, create/edit/delete desktop entries, then restore network and sync. | Local changes remain in the dirty queue while offline and upload/delete after connectivity returns. |
| Attachments | Add a local file attachment on desktop and a URI attachment on Android. Sync both clients. | Desktop caches local files locally but syncs portable `ATTACH;VALUE=URI` references; Android does not receive desktop cache paths. |
| Unknown properties | Add or preserve Android-specific `X-` properties through Android. Edit on desktop and sync. | Raw unknown iCalendar lines survive desktop parse/edit/serialize round trips. |
| Large collection smoke | Sync at least 500 mixed entries with several recurring tasks. | Sync completes without UI hangs, duplicate uploads, or data loss. |

## Pass Criteria

- No data-loss scenario is observed during a full Android-to-desktop-to-Android round trip.
- Dirty queue entries clear only after successful upload/delete.
- Conflicts require explicit user resolution.
- Read-only and offline states leave local changes queued.
- Unknown properties, attachment references, recurrence rules, and alarm lines are preserved unless explicitly edited by the user.
