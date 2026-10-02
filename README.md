# QuietInbox SMS — filtered SMS app for Xiaomi Mi 11 Lite

Works like the default SMS app, plus:

- **Inbox** tab = messages from saved contacts
- **Unknown** tab = messages from unsaved numbers / short codes / alphanumeric senders
- **Mute Unknown** toggle = no notification at all for unsaved senders (default ON).
  Turn it OFF to get a silent low-priority notification instead.
- **Filter toggle** = turn filtering off to show everything in Inbox (like stock app).

## How the "separate inbox" works

Android does not let a non-system app physically move rows out of the
system SMS provider. So QuietInbox keeps the standard provider (all SMS
stay in the stock database) and classifies at display + notify time via
`ContactsContract.PhoneLookup`:

- `SmsRepository.loadConversations()` tags each thread `isSavedContact`
- `MainActivity` tabs split Known / Unknown / All
- `SmsReceiver` (SMS_DELIVER, only fires when QuietInbox is the default
  SMS app) posts a loud notification for contacts, silent-or-none for unknown.

## Install on Mi 11 Lite (Android 11–13)

1. Install the APK: `app-debug.apk` (see Releases / Actions artifact).
   Allow "Install unknown apps" when prompted.
2. Open **QuietInbox SMS** → grant SMS + Contacts permissions.
3. Menu → **Set as default** → confirm. This is required for receiving
   SMS inside the app and for muting system notifications.
   (Only the default SMS app receives `SMS_DELIVER`; otherwise the stock
   Messaging app will still notify.)
4. Test: have an unsaved number text you → it appears under **Unknown**
   with no sound/vibration. Saved contacts → **Inbox** with normal notification.

## Build yourself

Easiest: open the `QuietInbox` folder in **Android Studio** and press
Run (it downloads the SDK + dependencies automatically).

Or build in the cloud — push this folder to a GitHub repo:
`.github/workflows/build-apk.yml` builds on every push and uploads
`app-debug.apk` as the `QuietInbox-apk` artifact (Actions tab →
download → install on the phone).

Command line (JDK 17 + Android SDK 34 required):

```
gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Notes / limits

- First load does a `PhoneLookup` per thread; results are cached per session.
- MMS display/sending is stubbed in v1 (receiving eligibility only). SMS send/receive is full.
- Dual-SIM send uses the system default SIM (v1).
- MIUI battery saver: lock QuietInbox in Recents + enable Autostart so
  `SmsReceiver` fires reliably.
