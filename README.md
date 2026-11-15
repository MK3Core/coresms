# CoreSMS

A personal SMS/MMS app for Android, built because the existing apps keep
showing "failed to download" on MMS with no reliable way to get the message
back.

**Status: early build.** CoreSMS can become the default SMS app, receive and
send plain texts, and show notifications. It can't download picture or group
messages (MMS) yet: any that arrive are saved so they can be downloaded once
that's built. Keep your usual messaging app as the default for everyday use
until then. [docs/RESEARCH.md](./docs/RESEARCH.md) has the full plan and
build order.

## Goals

- **SMS** through the standard Android APIs, as the phone's default SMS app.
- **MMS that doesn't lose messages.** Every incoming MMS notification is saved
  before downloading, downloads retry automatically when data comes back,
  and any failed message keeps a Retry button until the carrier expires it.
- **Images and video from any phone,** including HEIC photos and videos sent
  from iPhones.
- **Real group MMS.** Group messages go out as one MMS to everyone and come
  back into the same group conversation, never split into individual texts.
- **Emoji reactions** that work both ways with iPhones and Android phones.
- **System notifications** with reply, mark as read and Android's
  conversation features.
- **A clean, minimal design** with pinned conversations. See
  [docs/DESIGN.md](./docs/DESIGN.md).
- Later: **scheduled messages** and **reminders to reply**.

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

- `sms`: receiving (`SmsDeliverReceiver`), sending (`SmsSender`,
  `SmsStatusReceiver`), quick replies from the call screen
  (`HeadlessSmsSendService`) and `sms:` link parsing
- `mms`: incoming MMS notices (`WapPushDeliverReceiver`) and the saved
  notices waiting for MMS download support
- `notify`: notification channels and contact name lookup
- `compose`: the composer opened by "send a text" links
- `ui`: Compose screens and theme
- `DefaultSmsApp.kt`: the default SMS app role and permissions

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
