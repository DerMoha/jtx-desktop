# Release Packaging Checklist

Use this checklist for release candidates after tests pass.

## Prerequisites

- Use a clean checkout with no uncommitted source changes.
- Confirm the release version in `build.gradle.kts`.
- Run `./gradlew jvmTest` before packaging.
- Use the platform that matches the package being produced; native desktop packages are OS-specific.

## Package Matrix

| Platform | Gradle Task | Artifact | Required Checks |
|---|---|---|---|
| macOS | `./gradlew packageDmg` | `build/compose/binaries/main/dmg/*.dmg` | DMG builds, app launches, menu bar appears, tray/status integration does not crash, file-open `.ics` import works. |
| Windows | `./gradlew packageExe` | `build/compose/binaries/main/exe/*.exe` | Installer runs on a clean VM, app launches, tray menu works, shortcuts use Ctrl, `.ics` file association/open-with path imports. |
| Linux DEB | `./gradlew packageDeb` | `build/compose/binaries/main/deb/*.deb` | Package installs with `apt`, app launches from desktop entry, tray behavior is acceptable for the target desktop environment. |
| Linux RPM | `./gradlew packageRpm` | `build/compose/binaries/main/rpm/*.rpm` | Package installs with `dnf` or `rpm`, app launches from desktop entry, uninstall removes package-managed files only. |

## Smoke Test After Install

- Start the app and configure a disposable CalDAV account.
- Discover collections and run a sync.
- Create a journal, note, and task through the desktop UI.
- Use Quick Entry with `Cmd/Ctrl+Shift+N`.
- Import one `.ics` file through the app menu and by opening the file with the app.
- Export filtered search results to `.ics`.
- Export and restore a JSON backup.
- Quit and relaunch; verify window bounds, settings, reminders, and dirty queue state recover.

## Failure Triage

- Packaging task fails before bundling: inspect Gradle/JDK/Compose plugin output first.
- App launches but sync fails: run the Phase 9 sync tests and manual interoperability matrix.
- Platform integration fails: record OS version, desktop environment, package artifact path, and exact reproduction steps.
