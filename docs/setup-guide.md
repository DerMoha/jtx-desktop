# jtx Desktop Setup Guide

This guide explains how to use the desktop app as a companion to Android jtxBoard with a shared CalDAV account.

## Requirements

- A CalDAV server account, such as Nextcloud, Radicale, or another standards-compatible provider.
- Android jtxBoard configured for the same CalDAV account.
- Network access from the desktop app to the CalDAV server.
- If your provider supports app passwords, use an app password instead of your primary account password.

## Configure Android First

1. Install and configure Android jtxBoard with your CalDAV account.
2. Create or select the collections you want to share for journals, notes, and tasks.
3. Create one test journal, note, and task on Android.
4. Run Android sync and confirm the entries are visible on the CalDAV server or another CalDAV client.

## Configure Desktop Sync

1. Open the desktop app.
2. Go to Settings.
3. Enter the CalDAV server URL, username, and password or app password.
4. Select which components to sync: journals, notes, and tasks.
5. Click Discover Collections.
6. Choose the collection URL you want to sync.
7. Click Save Settings.
8. Click Sync Now from the toolbar, app menu, or tray menu.

## Verify Interoperability

After the first sync:

- Confirm Android-created journals appear on desktop as journals.
- Confirm Android-created notes stored as `VJOURNAL` without `DTSTART` appear on desktop as notes.
- Confirm Android-created tasks appear on desktop with due dates, priority, progress, reminders, recurrence, categories, color, attachments, comments, and relationships where supported.
- Edit one entry on desktop, sync desktop, then sync Android.
- Confirm Android receives the change without losing unsupported fields.

For release testing, use `docs/manual-interoperability-test-matrix.md`.

## Local Editing Behavior

- Desktop edits are stored locally first.
- Local creates, updates, and deletes are queued as dirty changes until sync succeeds.
- If the network is unavailable, the app shows Offline and keeps local changes queued.
- If the collection is read-only, desktop downloads remote changes but keeps local changes queued instead of uploading them.

## Conflicts

A conflict can happen when Android and desktop both edit the same remote object before either client sees the other change.

When desktop detects a conflict, it shows a conflict dialog with three choices:

- Keep Local: upload the desktop version over the server version.
- Keep Server: replace the local desktop copy with the server version.
- Keep Both: keep the server version and create a new dirty local copy for the desktop version.

Do not ignore conflicts before release testing; resolve them and sync again.

## Attachments

- Local file attachments selected on desktop are cached under the desktop app data directory.
- Sync exports portable `ATTACH;VALUE=URI` references.
- Desktop cache paths are not written to CalDAV objects, so Android should not receive machine-local file paths.
- Remote URI attachments from Android are preserved and can be opened from desktop detail views when the OS supports the URI.

## Backup And Restore

Use Settings to export a JSON backup before risky operations or migration testing.

- Export Backup writes a versioned JSON file containing entries, collections, sync metadata, and settings.
- Import Backup restores the JSON backup into the local database.
- `.ics` import/export is also available for selected entries and search results, but JSON backup is the safer full-local-state format.

## Troubleshooting

| Symptom | Action |
|---|---|
| Authentication rejected | Check username and password or create a new app password. |
| Collection not found | Run Discover Collections again and select the discovered collection URL. |
| Read-only state | Choose a writable collection or keep desktop as download-only for that collection. |
| Offline state | Restore network connectivity and run Sync Now. |
| Repeated server errors | Check server logs, provider status, and the sync issues dialog. |
| Android fields disappear | Stop testing and capture the original `.ics`, desktop-exported `.ics`, and exact edit steps; unknown properties should be preserved. |

## Release Checklist

Before distributing a build:

- Run `./gradlew jvmTest`.
- Run the packaging checklist in `docs/release-packaging-checklist.md`.
- Run the interoperability matrix in `docs/manual-interoperability-test-matrix.md`.
- Confirm JSON backup and restore works on the release build.
