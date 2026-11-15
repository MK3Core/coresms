# Backlog

Ideas, decisions to make and work that's deliberately deferred. Nothing here
is scheduled; it's a record so things don't get re-litigated or lost between
sessions. The build order is in
[docs/RESEARCH.md](./docs/RESEARCH.md#12-suggested-build-order) and the
design direction in [docs/DESIGN.md](./docs/DESIGN.md).

## Decided

- **License: GPL-3.0.** Code from Fossify Messages and QUIK (both GPL-3.0)
  can be reused with attribution, as can Apache 2.0 code from AOSP
  Messaging and android-smsmms.
- **Package name: `io.github.mk3core.coresms`.** Tied to the GitHub account,
  which works for F-Droid-style verification and a later Play Store listing.
  It can't change without breaking updates for installed copies.
- **Distribution:** GitHub Releases, installed with Obtainium on GrapheneOS.
  The Play Store may come later.
- **Signing key:** `coresms-release.keystore`, backed up outside git in
  `coresms-keystore-backup/` (see its README.txt). Every release must use
  the same key.

## Known gaps

- **Video compression.** Videos larger than the carrier's MMS limit are
  refused with a message. Media3 Transformer could re-encode them smaller.
- **Search** covers conversation names and the latest message only, not
  every message.
- **Delivery and read reports** aren't requested or shown.
- **Swipe actions** on conversations (DESIGN.md) aren't built; long-press
  covers pin, read/unread and delete for now.
- **Dual SIM:** messages go out on the default SMS SIM; there's no picker.
- **Bubbles** (floating chat heads) aren't set up, though notifications are
  already tied to conversation shortcuts.
- **Group previews** in the conversation list show "Group conversation"
  rather than the latest message (Android stores an MMS's subject as the
  preview, and group texts have none). Deliberately left this way.
- **App size.** Code shrinking (R8) is off, so the APK is larger than it
  needs to be. Turn it on once there's a way to test release builds.
- **Contact cards** (vCard) open in another app rather than inside CoreSMS.

## Decided since

- **Tuned against a real export (2026-11-15).** 3,718 messages from the
  owner's phone were analysed locally (counts only): group threading, number
  formats, text encodings, attachment types and reaction formats all checked
  out. Fixes from it: recovering MMS another app failed to download,
  detecting our own number from earlier sent MMS, whitespace-tolerant
  reaction matching (93% → 98% of reactions find their message), animated
  GIFs, loading older messages, faster group threads, ❤/❤️ as one reaction,
  and a grid for multi-photo messages.

- **PDU code:** ported from AOSP Messaging's `mmslib` (Apache 2.0) into
  `app/src/main/java/io/github/mk3core/coresms/mmslib`, without its
  `PduPersister`; CoreSMS stores messages with its own `MmsStore`.
- **"Actively texting" window:** a conversation stays on the first screen
  for 20 minutes after its last message (`MainActivity.ACTIVE_WINDOW_MS`).
- **Fold height:** a full screen minus a 72dp peek of the first read
  conversation. Tune after using it.

## Saved MMS notices from v0.1.0

v0.1.0 saved incoming MMS notices to `files/mms-notices/` instead of
downloading them. Since v0.2.0, `IncomingMms.importSavedNotices` processes
and deletes them at startup. Remove that code once no v0.1.0 installs remain.

## Later features

- **Scheduled messages.** Exact alarms (`SCHEDULE_EXACT_ALARM`), re-armed on
  boot. See RESEARCH section 8.
- **Reply reminders.** "Remind me" on notifications and conversations.
- **Backup / export** in a format compatible with SMS Backup & Restore.
- **Non-English iPhone reactions.** Start with English patterns; add others
  as real samples show up.
- **Unique design elements.** Ideas collected in docs/DESIGN.md.

## Play Store, if it happens

- **Developer account.** One-time fee; new personal accounts need a closed
  test with a minimum number of testers before production access. Check
  current requirements when starting.
- **SMS permissions declaration.** Play only allows SMS permissions for
  default SMS apps, through a permissions declaration form. CoreSMS
  qualifies, but expect a review.
- **Signing.** Play App Signing re-signs uploads with Google's key, so
  sideloaded installs won't update from the Play build. Plan a one-time
  reinstall, and make sure messages live in the system database (they do)
  so nothing is lost.
- **Google developer verification** becomes required on Google-certified
  devices worldwide in 2027. GrapheneOS doesn't include it, but other
  phones would, so register before distributing to anyone else.
