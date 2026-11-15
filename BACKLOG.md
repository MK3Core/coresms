# Backlog

Ideas, decisions to make and work that's deliberately deferred. Nothing here
is scheduled; it's a record so things don't get re-litigated or lost between
sessions. The build order is in
[docs/RESEARCH.md](./docs/RESEARCH.md#12-suggested-build-order).

## Decisions before the first code commit

- **License.** Fossify Messages and QUIK are GPL-3.0; copying any of their
  code makes CoreSMS GPL-3.0. AOSP Messaging and android-smsmms are Apache
  2.0. Choose GPL-3.0 (free to borrow from everything) or Apache 2.0 / MIT
  (borrow only from the Apache projects).
- **PDU code.** Port the MMS PDU parser/composer from AOSP Messaging, or
  depend on klinker's android-smsmms. Porting means more code to own but no
  unmaintained dependency.
- **Package name.** Pick it once (for example `com.mk3core.coresms`); it
  can't change without breaking updates for installed copies.
- **Signing key.** Create a dedicated release keystore and back it up like
  AutoTrack's. Every release must use the same key.

## Later features

- **Scheduled messages.** Exact alarms (`SCHEDULE_EXACT_ALARM`), re-armed on
  boot. See RESEARCH section 8.
- **Reply reminders.** "Remind me" on notifications and conversations.
- **Backup / export** in a format compatible with SMS Backup & Restore.
- **Non-English iPhone reactions.** Start with English patterns; add others
  as real samples show up.
- **Google developer verification.** Registration becomes required on
  certified devices globally in 2027. Register MK3Core before then so
  installs keep working.
