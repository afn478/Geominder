# Geominder

Geominder is an Android reminder app for scheduling reminders by time or location.

Named location presets can be configured under Settings > Preset locations. A preset pairs a
keyword such as “at home” with coordinates and a geofence radius; typing that keyword in a new
reminder creates the corresponding location trigger.

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

The default English resources use American English. Android's `values-en-rGB` overlay supplies
British spellings, terms, and examples for devices using the `en-GB` locale; `en-US` uses the
default resources.

To add a language, add its `SupportedLanguage` entry, pack rules, keyword JSON, and a matching
Android `values-<language>` resource overlay. Keep user-facing state as `UiText.Resource` so
Compose resolves it with the system locale at the UI boundary.
