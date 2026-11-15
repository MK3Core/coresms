# CoreSMS research: what it takes to build an Android SMS/MMS app

Written November 2026, before any code. It covers each requirement: the
Android APIs involved, how existing open-source apps handle it, and what's
hard. Android and carrier behavior changes often, so recheck anything marked
"verify" before building on it.

## Summary

Building a working SMS/MMS app is very doable for one developer, but the work
is uneven:

| Area | Difficulty | Why |
| --- | --- | --- |
| SMS send/receive | Easy | Public, stable APIs (`SmsManager`, `Telephony` provider). |
| Becoming the default SMS app | Easy, with fiddly setup | A fixed set of manifest components plus `RoleManager`. Sideloaded installs need one extra settings step (see below). |
| Notifications | Easy to medium | Standard `MessagingStyle` notifications. Making them look native (conversation shortcuts, bubbles, direct reply) takes more work. |
| MMS receive and **download retry** | **Hard** | Android hands you raw MMS PDUs. You parse them, download through `SmsManager`, and do your own retry and error handling. This is where other apps fail, and the main reason for the project. |
| MMS send | **Hard** | You build the PDU yourself and compress media to fit the carrier's size limit. |
| Group MMS | Medium | One MMS to many recipients, plus correct threading by participant set. You need to know your own number to get it right. |
| iPhone images and video | Medium | HEIC decodes natively on Android 9+. Video needs a real player. Carriers often transcode or shrink media in transit anyway. |
| iPhone reactions | Medium | Over SMS/MMS, reactions arrive as plain text ("Loved “…”", "Reacted 😂 to “…”"). You parse them and attach them to the right message. |
| RCS | **Not possible** | No public API for third-party apps. See below; this affects how iPhones reach you. |

The hard parts (MMS PDUs, retry, group threading) are solved in Apache-licensed
code in Google's AOSP Messaging app, so there's a proven reference to follow.

### The RCS catch

Since iOS 18, iPhones and Google Messages use **RCS** when both sides support
it. RCS gives typing indicators, full-quality media and native reactions.
**Third-party apps can't use RCS:** Android's RCS APIs are hidden and
allowlisted to Google Messages (and Samsung via a private arrangement).
Google's Jibe platform is for business messaging, not consumer apps.

What that means for CoreSMS:

- With CoreSMS as the default SMS app, everything goes over SMS/MMS, including
  iPhone chats. Media is limited by carrier MMS size limits (often around
  1 MB, sometimes less), and reactions arrive as text that we parse.
- **Before switching, turn off RCS chats in Google Messages** (Settings → RCS
  chats → off) and let it deregister. Otherwise iPhones and other Android
  phones may keep sending RCS to your number, and those messages never reach
  CoreSMS (or arrive late as an SMS fallback).
- That's a trade-off to accept up front. In return you get MMS retry and
  control that Google Messages doesn't give you.

## 1. Becoming the default SMS app

Since Android 4.4, only the default SMS app can write to the SMS/MMS database
and receive the "deliver" broadcasts. Every other app only gets a read-only
copy. So CoreSMS must be the default to do anything useful.

**Required manifest components** (Android won't offer an app as the default
without all four):

| Component | Intent action | Guarded by permission |
| --- | --- | --- |
| Broadcast receiver for SMS | `android.provider.Telephony.SMS_DELIVER` | `android.permission.BROADCAST_SMS` |
| Broadcast receiver for MMS | `android.provider.Telephony.WAP_PUSH_DELIVER`, MIME type `application/vnd.wap.mms-message` | `android.permission.BROADCAST_WAP_PUSH` |
| Service for quick reply from the dialer | `android.intent.action.RESPOND_VIA_MESSAGE` | `android.permission.SEND_RESPOND_VIA_MESSAGE` |
| Activity for compose | `ACTION_SEND` / `ACTION_SENDTO` with `sms:`, `smsto:`, `mms:`, `mmsto:` | none |

**Asking for the role:** On Android 10+ use
`RoleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)`. Older versions
used `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT`; our min SDK won't need it.

**Permissions:** `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`,
`RECEIVE_WAP_PUSH`, `READ_CONTACTS`, `READ_PHONE_STATE`,
`READ_PHONE_NUMBERS` (to know our own number), `POST_NOTIFICATIONS`
(Android 13+), plus `INTERNET`, `ACCESS_NETWORK_STATE` and
`CHANGE_NETWORK_STATE` for MMS.

**Sideloading gotcha (Android 15+):** Android's "restricted settings" now
covers the default SMS and dialer roles for apps installed outside an app
store. The first time you try to make CoreSMS the default, Android blocks it.
Then go to App info → ⋮ menu → **Allow restricted settings**, and try again.
It's a one-time step per install, and the README should walk through it.
Apps installed by Obtainium may or may not count as store installs; verify on
the phone.

**Google's developer verification:** Since September 30, 2026, Google requires
verified developers for apps on certified devices in Brazil, Indonesia,
Singapore and Thailand, going global in 2027. Direct sideloading and ADB
installs aren't covered by the first phase, and unverified apps can still be
installed through an "advanced flow". Registering MK3Core as a developer
(free for limited distribution, verify) would avoid trouble later.

## 2. SMS

- **Sending:** `context.getSystemService(SmsManager::class.java)`, then
  `createForSubscriptionId(subId)` for dual-SIM phones.
  `divideMessage()` + `sendMultipartTextMessage()` for long texts, with
  "sent" and "delivered" `PendingIntent`s to update status.
- **Receiving:** the `SMS_DELIVER` receiver gets PDUs.
  `Telephony.Sms.Intents.getMessagesFromIntent(intent)` turns them into
  `SmsMessage`s; multipart messages arrive together and are joined.
- **Storage:** the default app writes every message itself into
  `Telephony.Sms` (`content://sms`): inbox on receive, outbox → sent/failed on
  send. Threads come from `Telephony.Threads.getOrCreateThreadId()`.
- **Encoding:** emoji and non-Latin text force UCS-2, which cuts a segment
  from 160 to 70 characters. Show a segment counter in the composer.
- **Blocking:** `BlockedNumberContract` is the system block list, readable by
  the default SMS app. Check it before storing or notifying.

## 3. MMS (receive, download, retry)

This is the core of the project.

**How MMS delivery works:**

1. The carrier's MMS server (MMSC) stores the message and sends your phone a
   small WAP push, an **M-Notification.ind**. It contains the sender, size,
   an expiry time and a **content-location URL**.
2. The phone sees `WAP_PUSH_DELIVER` and the app has to download the message
   from that URL over **mobile data** (the carrier's MMS APN), even when you're
   on Wi-Fi.
3. The download returns an **M-Retrieve.conf** PDU with the full message:
   sender, all recipients (To/Cc), subject and parts (text, images, video,
   SMIL layout).
4. The app sends an **acknowledgement** back to the MMSC.

**APIs:** `SmsManager.downloadMultimediaMessage(context, locationUrl,
contentUri, configOverrides, downloadedIntent)` downloads into a file or
content URI you provide. Android takes care of bringing up the MMS APN. The
result `PendingIntent` reports success or an error code (`MMS_ERROR_*`, plus
an HTTP status for HTTP errors). Sending works the same way with
`sendMultimediaMessage()`.

**What Android doesn't give you:** a public PDU parser or composer. The
framework's `com.google.android.mms.pdu` classes are hidden, so every app
ships its own copy. AOSP Messaging includes one (Apache 2.0). Klinker's
`android-smsmms` library packages the same code and is used by Fossify and
QKSMS/QUIK.

**Why "failed to download" happens:**

- No mobile data at that moment (mobile data off, bad signal, airplane mode,
  Wi-Fi calling quirks).
- The carrier's MMSC was briefly unavailable or returned an HTTP error.
- Wrong or missing MMS APN settings (common on MVNOs).
- The message expired on the MMSC (expiry is usually days, set by the
  carrier).
- Apps give up after one or two attempts and then give no useful way to retry.

**CoreSMS design for retry:**

- On WAP push, **immediately save the notification** (content-location,
  expiry, size, sender, transaction id) to the provider as an MMS in the inbox
  with `m_type` = notification, and also to our own table. Nothing is lost
  even if the download fails.
- Download with **WorkManager**, with a network constraint (connected),
  exponential backoff, and an automatic retry when connectivity returns.
- Only retry temporary errors (no data, IO error, HTTP 5xx). Stop on errors
  that can't succeed (HTTP 404/410 means the MMSC no longer has it; 403 can
  mean an APN or identity problem).
- Show every failed message as a bubble with sender, size, the error in plain
  words and the expiry time, plus a **Retry** button that works until expiry.
- Keep a **Failed downloads** screen and a diagnostics view (APN in use,
  carrier MMS config, last HTTP status) to figure out carrier-specific
  problems.
- Retrying a download that previously failed is valid as long as the MMSC
  still holds it. The URL in the notification doesn't change. Reports from
  other platforms (Sailfish, Firefox OS) show retries fail when apps lose or
  corrupt the notification state, which is exactly what persisting it fixes.

**Carrier config:** `CarrierConfigManager` gives limits like
`KEY_MMS_MAX_MESSAGE_SIZE_INT`, `KEY_MMS_MAX_IMAGE_WIDTH_INT` /
`HEIGHT`, and `KEY_MMS_GROUP_MMS_ENABLED_BOOL`. Use them to size outgoing
media.

## 4. Group MMS

Two halves: send group messages as one MMS, and thread incoming ones into the
right group.

**Sending:** one MMS PDU with **every recipient in To** (not separate
messages, and not BCC). Carriers deliver it once per recipient and every
recipient sees all the others, so replies go to the whole group. Plain text in
a group still goes as MMS. Never fall back to sending individual SMS to each
member; that's the "split group" behavior to avoid. (Some apps let you choose
"send as individual texts"; Fossify 1.9 added that choice on first group send.
CoreSMS can make it a per-conversation option, off by default.)

**Receiving and threading:** a group MMS has the sender in **From** and
everyone else in **To/Cc**, including you. The thread is the set of all
participants minus yourself:

```
participants = {from} ∪ to ∪ cc − {my number(s)}
threadId = Telephony.Threads.getOrCreateThreadId(context, participants)
```

Getting "my number" right is crucial. If it's wrong, your own number becomes
a participant and the message lands in a different thread. Sources:
`SubscriptionManager.getPhoneNumber(subId)` (Android 13+), the
`Telephony.Mms.Addr` entries of messages we've sent, and a manual setting as a
last resort. Normalize every number to E.164 with **libphonenumber** before
comparing, or `+1 555…` and `555…` will look like different people (a top
cause of split threads according to carrier forums).

**On the iPhone side:** group texts with any Android member are MMS, and the
iPhone needs Settings → Messages → **MMS Messaging** and **Group Messaging**
turned on. If an iPhone friend's replies come in individually, that setting is
the first thing to check, not our app.

## 5. Images and video (including from iPhones)

- **Formats from iPhones:** photos are usually HEIC/HEIF, but carriers
  commonly transcode MMS images to JPEG. Videos are `.mov` / MP4 with H.264 or
  HEVC, and carriers usually transcode and shrink them heavily (blurry video
  is the carrier, not the app). Live Photos arrive as just the still image.
  Also expect GIF, PNG, vCard (`.vcf`) contacts and audio clips (AMR, M4A).
- **Display:** **Coil** (image loading for Compose) decodes HEIC via
  `ImageDecoder` on Android 9+ and plays animated GIF and WebP. Add `coil-video`
  for video thumbnails. Use **Media3 ExoPlayer** for video and audio playback.
- **Saving:** "Save to gallery" via `MediaStore` (no storage permission
  needed on Android 10+).
- **Sending:** use the Photo Picker (`ActivityResultContracts.PickVisualMedia`,
  no permission needed). Resize and re-encode to JPEG to fit the carrier's max
  message size and dimensions. Compress video with Media3 Transformer, or warn
  when it's too big.
- **Unknown parts:** show unknown attachments as a file chip that can be
  shared or opened elsewhere, so nothing is ever silently dropped.

## 6. Emoji reactions (Android and iPhone)

Over SMS/MMS, reactions are just text messages. There's no hidden field, so
the app parses them.

**Formats seen in the wild** (verify against real messages and collect
samples while testing):

| Sender | Example | Meaning |
| --- | --- | --- |
| iPhone, classic tapbacks | `Loved “See you at 6”` | ❤️ |
| | `Liked “…”` / `Disliked “…”` | 👍 / 👎 |
| | `Laughed at “…”` / `Emphasized “…”` / `Questioned “…”` | 😂 / ‼️ / ❓ |
| iPhone, iOS 18+ custom emoji | `Reacted 🔥 to “…”` | 🔥 |
| iPhone, removing | `Removed a heart from “…”`, `Removed 🔥 from “…”` | remove reaction |
| iPhone, on an image | `Loved an image` | ❤️ on the last image |
| Google Messages to non-RCS | `👍 to “…”`, `Reacted 👍 to “…”` | 👍 |

The quotes are curly quotes (“ ”), and long messages are cut off with "…".
iPhones set to other languages send translated wording; support English
first and keep the patterns in one table so others can be added.

**Matching a reaction to its message:** search the thread's recent messages
(roughly the last few hundred) for a body that equals or starts with the
quoted text, newest first, skipping messages sent by the reactor themselves
(people rarely react to their own messages). If nothing matches, show it as a normal text so nothing is hidden by mistake.

**Storing:** keep the original SMS in the provider (so other apps and backups
still see it) and add a row to our own `reactions` table pointing at the
target message. The UI shows reactions as chips under the bubble and hides
the raw text, with a "show original" option.

**Sending:** when you react, send an SMS in the iPhone-compatible form:
classic tapback words for the six standard reactions (`Loved “…”`) and
`Reacted 🔥 to “…”` for any other emoji. iPhones turn those into real
tapbacks, and Google Messages does too. Removing a reaction sends the matching
`Removed …` text. In groups the reaction goes to the whole group as MMS.

## 7. Notifications

- Channels: one per conversation (so users can set different sounds per
  chat, like Google Messages) plus a default channel. Use `NotificationChannel`
  with `setConversationId()`.
- **`NotificationCompat.MessagingStyle`** with a `Person` per sender, group
  conversation title and the last few messages.
- **Conversation shortcuts** (`ShortcutManagerCompat.pushDynamicShortcut`
  with a `LocusId`) so notifications appear in Android's "Conversations"
  section, can be marked as priority and support **bubbles**.
- Actions: **Reply** (`RemoteInput` direct reply), **Mark as read**, and
  later **Remind me** (reply reminder).
- Show a thumbnail for image MMS, and post a notification for failed MMS
  downloads with a Retry action.
- `POST_NOTIFICATIONS` runtime permission on Android 13+.

## 8. Later: scheduled messages and reply reminders

Both need only our own database and the alarm APIs:

- **Scheduled send:** store the draft and time in our database. Use
  `AlarmManager.setExactAndAllowWhileIdle()` with the `SCHEDULE_EXACT_ALARM`
  permission (Android 12+; the user approves it once in settings) so it sends
  on time even in Doze. Re-arm alarms on boot (`BOOT_COMPLETED`).
- **Reply reminders:** a "remind me in 1h / tonight / tomorrow" action on the
  notification and in the conversation. When the time comes, post a
  notification with Reply. WorkManager is accurate enough here.

## 9. Other things a daily-driver SMS app needs

- **Dual SIM:** `SubscriptionManager` for active SIMs, a SIM picker in the
  composer, and remember the SIM used per conversation.
- **Contacts:** `ContactsContract.PhoneLookup` for names and photos, and an
  observer to refresh when contacts change.
- **Search:** a full-text index (Room FTS) over message bodies.
- **Backup / restore:** messages live in the system provider, so they survive
  an app reinstall, but not a phone reset. An export/import (XML compatible
  with "SMS Backup & Restore" would be ideal) is worth having before trusting
  the app as a daily driver.
- **Keeping the provider as the source of truth:** we read and write messages
  to `content://sms` and `content://mms`, so switching back to Google Messages
  keeps all history. CoreSMS-only data (reactions, failed-download state,
  schedules, reminders, per-chat settings) goes in our own Room database keyed
  by provider message id.
- **Emergency alerts** (wireless emergency alerts) are handled by the system,
  not the SMS app. Nothing to do.

## 10. Open-source projects to learn from

| Project | Language / license | Status | What to use it for |
| --- | --- | --- | --- |
| [AOSP Messaging](https://android.googlesource.com/platform/packages/apps/Messaging/) | Java / Apache 2.0 | Google's reference app (and the base of LineageOS Messaging) | **Best reference for MMS:** PDU parsing and composing, download/retry state machine, group threading, carrier config. Apache license means code can be copied with attribution. |
| [Fossify Messages](https://github.com/FossifyOrg/Messages) | Kotlin / GPL-3.0 | Active (v1.9.1, July 2026) | Modern Kotlin take on everything: default-app setup, SMS/MMS, group format choice, scheduled messages, block list. Uses android-smsmms. |
| [QUIK](https://github.com/octoshrimpy/quik) | Kotlin / GPL-3.0 | Active continuation of QKSMS (v4.3.x, 2026) | Polished UI, scheduled messages, notifications, backup. Uses android-smsmms. |
| [QKSMS](https://github.com/moezbhatti/qksms) | Kotlin / GPL-3.0 | Abandoned | History; QUIK supersedes it. |
| [android-smsmms](https://github.com/klinker41/android-smsmms) | Java / Apache 2.0 | Old but still the library under Fossify and QUIK | A packaged version of the AOSP PDU code and send/receive helpers. |
| [Signal-Android](https://github.com/signalapp/Signal-Android) (pre-2023 history) and [Silence](https://git.silence.dev/Silence/Silence-Android) | Java / GPL-3.0 | Signal dropped SMS in 2023 | Historic MMS handling and lessons on carrier quirks. |

**License decision:** Fossify and QUIK are GPL-3.0. Reading them is fine, but
copying their code would make CoreSMS GPL-3.0 too. AOSP Messaging and
android-smsmms are Apache 2.0 and can be reused under any license with
attribution. Pick the license before the first code commit (see BACKLOG).

## 11. Proposed stack

Native Kotlin, not Capacitor like AutoTrack. SMS needs manifest receivers,
services and system APIs throughout, and a web layer would add a bridge to
every one of them.

- **Kotlin + Jetpack Compose** (Material 3) for the UI
- **Room** for CoreSMS's own data (reactions, MMS download state, schedules,
  reminders, per-conversation settings); the system Telephony provider stays
  the source of truth for messages
- **WorkManager** for MMS downloads/retries and sends; **AlarmManager** for
  exact scheduled sends
- **Coil 3** (+ video) for images; **Media3** ExoPlayer / Transformer for
  video
- **libphonenumber** for number normalization
- PDU code ported from AOSP Messaging (Apache 2.0) or taken from
  android-smsmms
- **Hilt** (or manual DI, the app is small) and Kotlin coroutines/Flow
- Min SDK 29 (Android 10, for `RoleManager`), target the latest SDK
- Same GitHub setup as AutoTrack: CI on pull requests, signed APK release on
  `vX.Y.Z` tags, Obtainium for updates, Dependabot

## 12. Suggested build order

1. **Skeleton:** Compose app, the four default-SMS components, role request,
   permissions, CI and release workflow. Installable APK that can become the
   default.
2. **SMS:** conversation list, thread view, send/receive, notifications with
   reply. At this point it can replace Google Messages for plain texts.
3. **MMS receive with retry:** WAP push → save notification → WorkManager
   download → parse → show images/video. Failed downloads screen with retry.
   This is the milestone the project exists for.
4. **Group MMS:** threading by participant set, own-number detection, sending
   group MMS.
5. **MMS send:** attachments, compression to carrier limits.
6. **Reactions:** parse incoming, show chips, send iPhone-compatible reactions.
7. **Polish:** conversation shortcuts/bubbles, search, block list, dual SIM,
   backup/export.
8. **Later:** scheduled messages, reply reminders.

Steps 1–3 are the real test of whether this is worth it. Testing needs a real
phone with a SIM on your carrier; the emulator can fake incoming SMS but not
real MMS downloads.

## Sources

- Android: [Telephony provider](https://developer.android.com/reference/android/provider/Telephony),
  [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager),
  [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager),
  [CarrierConfigManager](https://developer.android.com/reference/android/telephony/CarrierConfigManager),
  [Conversations and MessagingStyle](https://developer.android.com/develop/ui/views/notifications/conversations)
- [Android 15 restricted settings cover SMS and dialer roles (Android Authority)](https://androidauthority.com/android-15-restricted-settings-sideloading-3481098)
- [Developer verification timeline (Android Authority)](https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/),
  [what changed September 30 (Gadget Hacks)](https://android.gadgethacks.com/news/google-android-sideloading-restrictions-sept-30-explained/),
  [advanced sideloading flow (9to5Google)](https://9to5google.com/2026/08/18/google-gradually-rolling-out-androids-advanced-sideloading-ahead-of-developer-verification/)
- [RCS APIs limited to OEMs, not third-party apps (9to5Google)](https://9to5google.com/2019/07/30/android-rcs-apis-oems-not-third-party-apps/),
  [hidden allowlisted RCS API (XDA)](https://www.xda-developers.com/google-messages-rcs-api-third-party-apps/)
- [iOS 18 emoji tapbacks to Android (Gizmodo)](https://gizmodo.com/your-emoji-reacts-will-finally-work-correctly-on-texts-from-iphone-to-android-2000522159),
  [tapback reactions in iOS 18 (MacRumors)](https://www.macrumors.com/how-to/ios-use-new-tapback-reactions-messages),
  [Google Messages SMS reactions to iPhones (9to5Google)](https://9to5google.com/2022/09/29/google-messages-sms-reactions/)
- [Group texts splitting into separate conversations (iPhone Life)](https://iphonelife.com/content/group-text-split-separate-conversations-heres-fix),
  [Ting group messaging troubleshooting](https://help.tingmobile.com/hc/en-us/articles/205421968-Troubleshooting-Group-Messaging)
- [MMS retry failures on Sailfish](https://forum.sailfishos.org/t/4-3-0-12-mms-works-but-mms-retry-fails-stays-waiting-forever/9268),
  [MMS retry policy discussion (Mozilla bug 862263)](https://bugzilla.mozilla.org/show_bug.cgi?id=862263)
- [Fossify Messages](https://www.fossify.org/apps/messages/),
  [QUIK](https://github.com/octoshrimpy/quik)
