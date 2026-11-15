# mmslib

MMS PDU parsing and composing (WAP-230/OMA MMS encapsulation), copied from
Google's AOSP Messaging app:
https://android.googlesource.com/platform/packages/apps/Messaging/+/refs/heads/main/src/com/android/messaging/mmslib

Licensed under the Apache License, Version 2.0 (see each file's header),
which is compatible with CoreSMS's GPL-3.0.

Changes from upstream (2026-11-15):

- Package renamed from `com.android.messaging.mmslib` to
  `io.github.mk3core.coresms.mmslib`.
- `PduPersister` and the DRM/cache helpers in `util/` were left out; CoreSMS
  stores messages with its own Kotlin code (`mms/MmsStore.kt`).
- `PduParser`: the multipart content-type constants from AOSP's
  `ContentType` class are inlined as strings.
- `PduComposer` and `CharacterSets`: `androidx.collection.SimpleArrayMap`
  replaced with `java.util.HashMap`.
