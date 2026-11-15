# CoreSMS

A personal SMS/MMS app for Android, built because the existing apps keep
showing "failed to download" on MMS with no reliable way to get the message
back.

**Status: early, but usable for testing.** CoreSMS sends and receives texts,
picture messages and group messages, shows reactions from iPhones and
Android phones, and keeps every MMS until it downloads. It hasn't had much
real-world testing yet, so expect rough edges.
[docs/RESEARCH.md](./docs/RESEARCH.md) has the background and plan.

## What it does

- **Conversations** with contact names and photos. The home screen shows
  pinned conversations across the top, then everything newest first, with
  unread ones in bold.
  Long-press a conversation to pin, mark read/unread or delete it.
- **Two themes:** Boringer (the default), plain monospace text in white on
  black, and Boring, rounded blue bubbles. Switch in Settings.
- **Texts and picture messages** in one thread, with iMessage-style bubbles,
  photos and videos inline (iPhone HEIC photos included), tap for full screen,
  long-press to save or share.
- **MMS that doesn't get lost.** Each incoming MMS is saved the moment the
  carrier announces it. Downloads retry on their own when they fail, and a
  message that still won't download shows why, when it expires, and a Retry
  button that asks the carrier again, even past the stated expiry. If the
  carrier no longer has it, the message says so and offers **Ask to
  resend**. Long-press any picture message → **Details** for its full
  history: when the carrier announced it, every attempt and the answer.
- **Recovering old failed downloads.** MMS that another app gave up on
  ("failed to download") are found at startup and quietly requested from
  the carrier again. Recovered messages appear where they belong in the
  conversation.
- **Group messages** go out as one MMS to everyone and come back into the
  same conversation. Start one by adding several people in New message.
  CoreSMS needs your own number to keep groups together: it uses the SIM's,
  or finds it in messages your previous app sent, or you can enter it in
  Settings.
- **Sending photos and videos.** Photos are resized to fit your carrier's
  MMS limit. Videos are sent as-is if they fit (most carriers allow about
  1 MB); compressing longer videos isn't built yet.
- **Reactions** that work with iPhones: tap a message to react (tap and hold for copy,
  details and delete), and
  reactions from iPhones and Google Messages show on the message they're for
  instead of as separate texts, listed under the message with who sent
  each one.
- **Notifications** per conversation with Reply and Mark as read, shown in
  Android's Conversations section.
- Share photos from the gallery straight into a conversation, and "Send
  text" links from other apps open in CoreSMS.

## Not planned

**RCS** (the "chat" features in Google Messages) isn't available to
third-party apps, so CoreSMS uses SMS/MMS only. Turn off RCS in Google
Messages before switching. [docs/RESEARCH.md](./docs/RESEARCH.md#the-rcs-catch)
explains why and what it changes.

## Install (Android)

Signed APKs are published on the
[Releases page](https://github.com/MK3Core/coresms/releases). Requires
Android 10 or newer.

**With Obtainium (recommended, gets updates automatically):**

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Tap **Add App** and paste `https://github.com/MK3Core/coresms`.
3. Install it from Obtainium. New releases show up as updates.

**Making it the default SMS app:** open CoreSMS and tap **Set as default SMS
app**. On Android 15 and newer (including GrapheneOS), Android blocks this
the first time for apps installed outside an app store. When it does:

1. Open **App info** for CoreSMS (the app has a button for it).
2. Tap the **⋮** menu and choose **Allow restricted settings**.
3. Go back to CoreSMS and tap **Set as default SMS app** again.

To switch back, use **Change default SMS app** in CoreSMS or Android's
Settings → Apps → Default apps → SMS app. Your messages stay in Android's
message database, so the other app sees everything CoreSMS received or sent.

## Development

Native Android app in Kotlin with Jetpack Compose. Requires JDK 17+ and the
Android SDK (Android Studio provides both).

```sh
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A debug build has a different signature from release builds, so uninstall
the release version before installing a debug one (and the other way round).

Project layout (`app/src/main/java/io/github/mk3core/coresms`):

- `data`: reading conversations and messages from Android's SMS/MMS
  database, contacts, own-number detection, pins and settings
- `sms`: receiving (`SmsDeliverReceiver`), sending (`SmsSender`,
  `SmsStatusReceiver`), quick replies from the call screen
  (`HeadlessSmsSendService`) and `sms:` link parsing
- `mms`: MMS notifications (`WapPushDeliverReceiver`, `IncomingMms`),
  download with retries (`MmsDownloadWorker`), storage (`MmsStore`), sending
  (`MmsSender`, `MediaPrep`) and the carrier transport (`MmsTransport`)
- `mmslib`: MMS PDU parsing and composing, from AOSP Messaging (Apache 2.0,
  see its README)
- `reactions`: reading and writing iPhone-style reaction texts
- `send/MessageSender.kt`: the single entry point for sending (SMS for one
  person, otherwise one MMS)
- `notify`: notifications and their Reply / Mark as read actions
- `compose`: the new-message screen (also handles links and shares)
- `ui`: Compose screens and theme; `ThreadActivity`, `ViewerActivity` and
  `SettingsActivity` sit at the top level

Testing SMS and MMS needs a real phone with a SIM. The emulator can fake
incoming SMS but can't download real MMS.

### Releasing

Pushing a `vX.Y.Z` tag runs `.github/workflows/release-apk.yml`, which builds
a signed release APK and publishes it as a GitHub Release (the version name
comes from the tag):

```sh
git tag v0.1.0
git push origin v0.1.0
```

Signing uses the repository secrets `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`.
Every release must be signed with the same key, or Android will refuse to
update existing installs. Running the workflow by hand from the Actions tab
builds a dev version instead, published as a prerelease tagged
`v0.0.<run number>-dev`.

## Roadmap

Planned work and deferred decisions are in [BACKLOG.md](./BACKLOG.md).

## License

CoreSMS is free software under the [GNU General Public License v3.0](./LICENSE).
