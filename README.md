# Geominder

Geominder is an Android reminder app for scheduling reminders by time or location.

## Build locally

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Languages

The parser uses the first supported language in Android's ordered system locale list, falling back
to English. The current language packs cover English, German, French, Italian, Spanish, Russian,
Japanese, Chinese, and Korean. Each pack keeps its clock order, day-part modifiers, relative-time
words, and default keyword table together in `TimeLanguagePacks.kt` and
`app/src/main/resources/reminder_keyword_times_*.json`.

To add a language, add its `SupportedLanguage` entry, pack rules, keyword JSON, and a matching
Android `values-<language>` resource overlay. Keep user-facing state as `UiText.Resource` so
Compose resolves it with the system locale at the UI boundary.
