# Contributing to Geominder

Thanks for helping improve Geominder. This guide covers the local Android setup, common build tasks,
device testing, and the project conventions that keep changes easy to review.

## Prerequisites

- Android Studio with its embedded JDK selected for Gradle.
- Android SDK Platform 36 and the SDK tools required by Android Studio.
- An Android device or emulator running API 29 or newer for app testing.
- `adb` for installing and exercising a debug build on a device or emulator.
- A Google APIs emulator image when testing Google Play services location behavior.

The app's source and bytecode target Java 17. CI currently builds with Temurin 17. Local Gradle
commands should use Android Studio's embedded JDK, as described above. The repository includes the
Gradle Wrapper and declares Gradle 8.13, so a system-wide Gradle installation is unnecessary.

## Get the project ready

1. Fork or clone the repository and open its root directory in Android Studio.
2. Let Android Studio create or update `local.properties` with the path to your Android SDK.
3. Wait for Gradle sync to finish.
4. Select an API 29+ device or emulator and run the `app` configuration.

`local.properties`, IDE metadata, and build outputs are ignored by Git. Keep them out of commits.

### Command-line setup

On macOS, the embedded JDK is usually located at:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Use the equivalent `Android Studio/jbr` path on Windows or the embedded JDK path shown by Android
Studio on Linux. Set this variable in each shell session before running the wrapper.

## Build and test

Run commands from the repository root.

| Command | Purpose |
| --- | --- |
| `./gradlew assembleDebug` | Build the debug APK. |
| `./gradlew testDebugUnitTest` | Run the JVM unit-test suite. |
| `./gradlew lint` | Run Android lint. |
| `./gradlew assembleRelease` | Build the minified release variant. Signing is not configured in this repository. |
| `./gradlew tasks --all` | List available Gradle tasks. |

The main local verification command is:

```bash
./gradlew testDebugUnitTest lint assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The first build may download the Gradle distribution and dependencies from the configured repositories.

## Install on a device

With a USB-debuggable phone or emulator available:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If more than one device is connected, pass the target serial with `adb -s <serial> ...`.

For a meaningful manual check, test the following flows when your change touches them:

- Fresh install and the notification, exact-alarm, and location permission states.
- A time reminder with the screen locked.
- An arrival reminder with precise and background location enabled, including while the app is
  closed.
- A reminder with both a time trigger and a location trigger.
- Editing detected triggers, clearing a trigger, and reopening a completed reminder.
- Snoozing, dismissing, completing, moving to trash, restoring, and permanently deleting.
- Exporting and importing an `.ics` file, including an update with a matching reminder identifier.
- Device reboot or app replacement when scheduling or boot-resync code changes.
- Each affected locale when parser or translated-resource code changes.

## Project layout

The project is a single Android application module. The main packages are organized by responsibility:

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/com/afn478/geominder/domain` | Reminder and trigger models plus repository contracts. |
| `app/src/main/java/com/afn478/geominder/data` | Room database entities, mapping, and persistence. |
| `app/src/main/java/com/afn478/geominder/parser` | Android-free natural-language time and coordinate parsing. |
| `app/src/main/java/com/afn478/geominder/alarm` | Alarm planning, permissions, scheduling, and receivers. |
| `app/src/main/java/com/afn478/geominder/geofence` | Location access, geofences, arrival verification, and labels. |
| `app/src/main/java/com/afn478/geominder/alert` | Notifications, full-screen alerts, and alert actions. |
| `app/src/main/java/com/afn478/geominder/backup` | iCalendar encoding, decoding, and document-picker integration. |
| `app/src/main/java/com/afn478/geominder/localization` | Supported languages and localized UI text handling. |
| `app/src/main/java/com/afn478/geominder/settings` | Preferences, keyword presets, appearance, and permission policy. |
| `app/src/main/java/com/afn478/geominder/ui` | Jetpack Compose screens and ViewModels. |
| `app/src/test` | JVM unit tests for domain, parser, scheduling, backup, settings, and UI logic. |
| `app/src/main/res/values*` | Android strings and theme resources for each supported locale. |
| `app/src/main/resources` | Built-in language-specific preset-time keyword tables. |
| `app/schemas` | Exported Room schema history. |

## Development conventions

- Follow Kotlin's official code style, already configured in `gradle.properties`.
- Keep business logic independent of Android APIs when practical so it can be tested on the JVM.
- Add or update focused unit tests with behavior changes, especially for parser, scheduling, backup,
  permission, and persistence logic.
- Keep user-facing strings in Android resources. Use `UiText.Resource` through the UI boundary so
  translations resolve with the active locale.
- Keep public behavior and documentation aligned with the actual permission and scheduling fallbacks.
- Avoid committing generated APKs, build reports, IDE files, or local SDK configuration.

## Adding or changing a language

Language work spans parser rules, preset data, and Android resources:

1. Add or update the language in `SupportedLanguage.kt`.
2. Add its lexical and date/time rules to `TimeLanguagePacks.kt`.
3. Add the built-in keyword-time table at
   `app/src/main/resources/reminder_keyword_times_<language>.json`. English uses
   `reminder_keyword_times.json`.
4. Add the matching Android string overlay under `app/src/main/res/values-<language>/strings.xml`.
5. Add parser coverage to `TimeLanguagePacksTest` and `ReminderTextParserTest`.
6. Check date order, day-part placement, relative dates, relative durations, and localized examples.

The app and parser use the first supported language in Android's ordered locale preferences by
default. A language selected in **Settings > Language** applies to both the interface and parser.
Custom preset times are persisted with their source language and migrated when the active language
changes. Keep user-facing state as `UiText.Resource` so Compose resolves it at the UI boundary.

The `values-en-rGB` overlay contains British English spellings, terms, and examples. Keep the
default English resources American English when changing regional behavior.

## Database and backup changes

Room schema files live in `app/schemas`. When changing a persisted entity:

1. Update the database version and add an explicit migration.
2. Run the relevant database and mapper tests.
3. Confirm the new schema export is present under `app/schemas`.
4. Consider how the change should round-trip through the iCalendar codec.
5. Add or update backup tests for new fields, malformed input, duplicate identifiers, and scheduling
   failures when applicable.

The backup format uses standard iCalendar VTODO records plus Geominder extension properties for
fields such as source text, status, tags, enabled state, snooze state, and location details. Preserve
stable reminder identifiers so imports can update existing data safely.

## Pull requests

Before opening a pull request:

- Explain the user-visible behavior or maintenance reason for the change.
- Include tests for the affected logic.
- Run `./gradlew testDebugUnitTest lint assembleDebug` with Android Studio's embedded JDK.
- Test on a device or emulator when the change affects Compose UI, permissions, alarms, geofences,
  boot behavior, or backup document flows.
- Review `git diff` for generated files, accidental localization changes, and sensitive data.
- Keep commits focused and update the README or this guide when workflows change.

## Continuous integration

`.github/workflows/build.yml` runs manually through `workflow_dispatch`. It uses Temurin 17, builds
the debug APK, and uploads the APK as the `geominder-debug-apk` workflow artifact.

## Useful references

- [Build your app from the command line](https://developer.android.com/build/building-cmdline)
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Request runtime permissions](https://developer.android.com/training/permissions/requesting)
