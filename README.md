# Geominder

Geominder is an Android reminder app for scheduling reminders by time or location automatically detected from the entered text.

Reminder data is stored on the device. You can export and import reminders as iCalendar (`.ics`)
files.

## Features

- Schedule reminders by time or location.
- Use natural-language dates, times, relative durations, weekdays, and coordinates.
- Use a time trigger and a location trigger in the same reminder.
- Edit detected triggers before saving.
- Snooze alerts for 10 minutes, dismiss them, or mark them done.
- Use color tags, sorting, trash, restore, and bulk actions.
- Choose a theme and accent color.

## Create a reminder

1. Tap **Add reminder**.
2. Enter the reminder text.
3. Check the detected time or location. Tap a trigger to edit it, or open the details to add one.
4. Save the reminder.

The parser runs locally. It uses the first supported language in Android's ordered language
preferences and falls back to English.

### Examples

| You type | Detected trigger |
| --- | --- |
| `Call Mom tomorrow at 6:00 PM` | Tomorrow at 18:00 |
| `Check the oven in 20 minutes` | 20 minutes from now |
| `Send it next Monday` | The next Monday at 09:00 |
| `Arrive at 40.7128, -74.0060` | The supplied coordinates |
| `Call me when at home` | A saved `at home` location preset |

A time without a date moves to the next day if it has already passed. A date without a time uses
09:00.

## Location reminders

To save a named location:

1. Open **Settings > Preset locations**.
2. Tap **Add location preset**.
3. Enter a keyword, coordinates, and radius in meters.
4. Save the preset and use its keyword in a reminder.

The default radius is 100 meters. Change it under **Settings > Location defaults**, or adjust it
for an individual reminder.

Location reminders need precise location access. Allow background location if they should work while
the app is closed.

## Permissions

The **Settings > Permissions** page shows the current status of:

- Notifications, required for reminder notifications on Android 13 and later.
- Exact alarms, which improve timing on Android 12 and later. Android can use a less precise fallback
  when this access is unavailable.
- Full-screen alerts, which can show a lock-screen alert on Android 14 and later.
- Precise and background location for arrival reminders.

Return to Geominder after changing a permission so the status can refresh.

## Manage reminders

You can sort and filter reminders, mark them done, reopen completed reminders, and move them to
trash. Trash items can be restored or permanently deleted. Bulk selection is available for these
actions.

## Backup and restore

Open **Settings > Backup** and choose **Export .ics** or **Import .ics**. Android's document picker
handles the file. The default export name is `geominder-reminders.ics`.

Imports update an existing reminder when its identifier matches. Other reminders are added as new
items.

## Build from source

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, build, test, and device instructions.

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## License

Geominder is available under the [MIT License](LICENSE).
