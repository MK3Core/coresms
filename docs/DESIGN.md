# CoreSMS design direction

How the app should look and feel. The first version of all of this shipped
in v0.2.0; current choices, open questions and ideas are at the end.

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

**Back to a plain list** (v0.4.3): pinned conversations across the top,
then every conversation newest first, with unread ones in bold with a
count. Two experiments came first and were dropped after real use: read
conversations starting a screen further down (felt like empty space), and
an "Earlier" drawer pulled up from the bottom (felt odd). The unique design
idea is still open.

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

## Current choices (tune after real use)

- A conversation you're actively texting in stays above the fold for 20
  minutes after its last message.
- Every message shows its time: under the bubble, or after the sender's
  name above it in a group. Tapping a photo or video opens it full screen (pinch to zoom,
  Save, Share); tapping a text does nothing, so stray taps are harmless.
  Tap and hold shows the reaction row plus **Actions…**, which opens View,
  Copy, Details and Delete, one step away from reactions so they can't be
  hit by accident. (Tried and dropped: tap to react with double-tap to
  open, because every single tap had to wait 300ms; and tap to react with
  View in the hold menu, because opening photos is the more common tap.)
- The composer's "+" opens one panel with **Photos | GIFs | Faces**. GIFs
  come from GIPHY at its loosest rating (R), trending until you search,
  and the largest size that fits the carrier's MMS limit is attached.
  Faces are built-in text faces (kaomoji like (╯°□°)╯︵ ┻━┻) added to the
  message box, recent ones first.
  Reactions are a quiet line under the message listing each emoji and who
  sent it, always visible; there's no badge on the bubble's corner.
- Two themes, chosen in Settings:
  - **Slick** (internally "Retro", the default): monospace text, white on true black, with
    color only where it means something (red for failures). Your messages
    sit on dark gray, theirs in a thin outline. No circles: avatars are
    initials as text like `CC` (`#` for an unnamed number, `CA+` for a group), the
    new-message button is a boxed `+ new`, and the remaining buttons and
    badges are small rounded squares. Always dark. A first version with phosphor green, amber, a blinking
    cursor and boxed avatars was too much; plain is the point.
  - **Boring** (internally "Modern"): a fixed CoreSMS blue for your bubbles, light gray for
    theirs, white background in light mode and true black in dark mode.
  Wallpaper colors (Material You) aren't used.
- One to three emoji with no text are shown large, without a bubble.
- A message containing a verification code or a US street address gets a
  small **Copy 482913** / **Copy address** button under it. Codes need a
  word like "code" or "verify" nearby and skip prices, phone numbers, times
  and years in dates; addresses need a house number, street name and street
  type. Copied codes are marked sensitive so Android hides them in the
  clipboard preview. Tuned against a real message history: every code and
  address found, no false ones.
- Popup menus in Slick are true black with the same thin outline as
  incoming bubbles, instead of Material's raised gray.
- Opening a conversation with unread messages puts a thin **New messages**
  line above the oldest one, about three quarters of the way up the screen,
  so the new ones read downward from it. Messages count as read only after
  the list has sat at the newest message for 500ms, which also removes the
  line; leaving earlier keeps the conversation unread. A message arriving
  while you're scrolled up gets the line instead of pulling you down; you're
  only moved to the bottom if you were already there or sent it yourself.

## Open questions

- What the other unique design elements are.
- Whether to offer Material You colors as an option.

## Ideas for unique elements

(Add here as they come up.)
