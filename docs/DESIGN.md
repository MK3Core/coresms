# CoreSMS design direction

How the app should look and feel. Nothing here is built yet: the current
setup screen is a placeholder, and the real UI arrives with the conversation
list and thread screens (build step 2 in [RESEARCH.md](./RESEARCH.md)).

## Principles

- **Clean and minimal.** Few elements on screen, lots of space, no clutter.
  If something isn't needed to read or reply, it's hidden behind a tap or
  long-press.
- **Crisp and easy to read.** Strong type hierarchy, high contrast, and
  bubbles that are easy to scan: you should know who said what at a glance.
- **Familiar cues** from iMessage and Instagram/Facebook Messenger:
  - Rounded message bubbles, yours on the right in an accent color, theirs
    on the left in a neutral tone.
  - Consecutive messages from the same person grouped tightly, with the
    corners between them flattened and the name/avatar shown once.
  - Timestamps hidden by default, with a centered time divider when there's
    a gap, and swipe or tap to reveal exact times.
  - Reactions as small chips pinned to the corner of the bubble they react
    to.
  - Images and video shown edge to edge in rounded frames, not as file
    attachments.
  - A compact composer pinned to the bottom with the send button inside the
    field.
- **Some unique CoreSMS elements.** To be decided; ideas go below as they
  come.

## Conversation list: "only what needs you" (signature feature)

The home screen shows **only conversations that need attention**. Everything
already read lives "below the screen": scroll down and your past
conversations come into view. Clearing your messages leaves a calm, nearly
empty screen instead of a wall of history.

**What's above the fold** (the first screen, in this order):

1. **Pinned conversations**, always, as a compact row of avatars like
   iMessage. A pinned chat with unread messages shows a badge on its avatar.
2. **Unread conversations**, newest first, each clearly marked (bold name,
   unread count, message preview).
3. **Failed MMS downloads**, treated as unread, so a message waiting for a
   retry is never out of sight.

**When nothing is unread,** the space shows a quiet "all caught up" state
(a short line of text, maybe the time of the last message) with a subtle
hint that past conversations are below, such as a small chevron or the top
edge of the first read conversation peeking up from the bottom.

**Below the fold:** read conversations, newest first, in a lighter style
(regular weight, muted preview). It's one continuous scroll, not a separate
screen or tab, so it feels like the history is physically underneath. The
fold itself is a deliberate gap: the read list starts at least one screen
height down, so the first screen really is just what needs you. Scrolling
back to the top hides the history again.

**Details to get right:**

- **No jumping.** When you open an unread conversation and come back, it
  stays where it was until you leave the list, then settles below the fold.
  Rows never move under your finger.
- **Search** is always one tap away at the top, and it covers every
  conversation, read or not.
- **Starting a new chat** (FAB or button) is available from the empty
  state too.
- **Mark as unread** (swipe or long-press) brings a conversation back above
  the fold, which works as a lightweight "deal with this later". Reply
  reminders (in the backlog) can do the same automatically when they fire.

**Swipe actions** for pin, mark read/unread, and archive/delete.

## Open questions

- Should a conversation you're actively texting in (read, but last message
  only minutes ago) stay above the fold for a while, or drop below as soon
  as it's read?
- Should the gap before the read list be a full screen height, or smaller,
  with the history fading in as you scroll?
- What the other unique design elements are.
- Theme: a fixed CoreSMS palette, Android's wallpaper colors (Material You),
  or both as a setting. Dark mode follows the system either way.

## Ideas for unique elements

(Add here as they come up.)
