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

## Still to decide

- **PDU code.** Port the MMS PDU parser/composer from AOSP Messaging, or
  depend on klinker's android-smsmms (or Fossify's fork of it). Porting
  means more code to own but no unmaintained dependency. Decide at step 3.

## Saved MMS notices from early builds

Until MMS download exists, `WapPushDeliverReceiver` saves each incoming MMS
notice to `files/mms-notices/` instead of dropping it. When step 3 lands,
process these first: parse each notice, skip any past their expiry, and
download the rest. Then delete the folder and this note.

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
