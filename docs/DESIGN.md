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

## Conversation list

- **Pinning.** Pin conversations to the top. Pinned chats get their own
  section (for example a row of avatars like iMessage, or a compact list),
  and pin order can be rearranged.
- **Ordered by activity.** Below the pins, conversations sort by newest
  message, with unread conversations clearly marked (bold name, dot, unread
  count).
- **Swipe actions** for pin, mark read/unread, and archive/delete.
- **Failed MMS stands out.** A conversation holding a message that failed to
  download shows that in the list, so it's never missed.

## Open questions

- "Organized by notifications": does that mean unread conversations float
  to the top above read ones (rather than strict newest-first), or a
  separate section for conversations with notifications waiting?
- What the unique design elements are.
- Theme: a fixed CoreSMS palette, Android's wallpaper colors (Material You),
  or both as a setting. Dark mode follows the system either way.

## Ideas for unique elements

(Add here as they come up.)
