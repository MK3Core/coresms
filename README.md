# CoreSMS

A personal SMS/MMS app for Android, built because the existing apps keep
showing "failed to download" on MMS with no reliable way to get the message
back.

**Status: research and planning.** There's no app yet. See
[docs/RESEARCH.md](./docs/RESEARCH.md) for what it takes and the planned
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
- Later: **scheduled messages** and **reminders to reply**.

## Not planned

**RCS** (the "chat" features in Google Messages) isn't available to
third-party apps, so CoreSMS uses SMS/MMS only. Turn off RCS in Google
Messages before switching. [docs/RESEARCH.md](./docs/RESEARCH.md#the-rcs-catch)
explains why and what it changes.

## Roadmap

Planned work and deferred decisions are in [BACKLOG.md](./BACKLOG.md).
