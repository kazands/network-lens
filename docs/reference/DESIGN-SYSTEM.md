# Network Lens interface

Network Lens is a calm, precise tool for finding and understanding devices on
the current Wi-Fi. Home users and enthusiasts come first. Network technicians
can inspect the same results in source-labelled technical details.

This document describes the Compose design refinement introduced in
0.3.36. It preserves the existing brand, discovery rules and navigation model.
It is not a claim of improved recognition or an independently verified inventory.

## Colour and surfaces

`LensTheme.kt` defines the static light and dark Material schemes, including
fixed accent roles. The brand remains signal green (`#0B6B51`) on warm paper
(`#F5F6F1`), and mint (`#A5E4BB`) on dark moss (`#101A17`).

Use `background` for the page, `surface` for device cards and details, and
`surfaceContainerHigh` for menus, dialogs and the scan sheet. Retain a 65% scrim
behind dialogs and sheets. Decorative separators use `outlineVariant`; input
boundaries use `outline`. Do not interpolate all foreground/background colours
when changing themes: both schemes must remain readable immediately.

Only icon tiles receive category colour: blue for network equipment, teal for
computers, muted violet for media, warm amber for smart home and neutral stone
for unnamed responders. This phone and the gateway use brand tones. Category
colours follow the existing physical-device filters; an audio capability does
not recolour a recognised thermostat. Icons and text remain the primary cues;
colour does not indicate safety, trust or ownership.

## Typography and shape

Keep system sans-serif text and monospace addresses. Text sizes remain scalable;
do not truncate device names or addresses to make a layout fit. Preserve the
existing left-to-right IPv6 grouping and exact-copy behaviour.

`LensTypography` owns the Material type roles. `LensShape` provides 28dp hero
and dialog corners, 16dp device/detail cards, 12dp icon/fact tiles and fields,
and round small badges. Keep 16dp page margins and 48dp minimum action targets.

## Scan card and results

Network name and scope occupy separate lines. The scan action stays explicit:
**Scan Wi-Fi** becomes **Stop scan** while work is active. The current device
count updates directly, without rolling digits or debounce. Use 26sp count/title
text normally and a stacked 20sp layout at enlarged text or narrow available
width; the count must not dominate the device list.

The list says "devices" for the app's grouped results. Scan details retain
separate device-group and address-endpoint counts. Every grouped address remains
available in device details. Neither wording nor colour changes grouping rules.

## Motion

`LensMotion.kt` owns one focal animation: a restrained radar sweep and ping
inside the visible scan card. It replaces the separate spinner and linear bar.
The rim represents only the first address pass. Afterwards the sweep continues
with explicit "Identifying devices" text; there is no invented overall 100%.

The radar is decorative. The existing textual status owns accessibility
announcements. A newly observed successful scan with at least one device gets
one short closing ring. Stop, failed/unfinished checks, network loss and empty
results do not. Opening details and returning must not replay completion.

Observe Android's animation-duration setting while the app is open. With
animations disabled, show the static radar and current progress. Do not keep an
infinite transition while the card is off-screen, the Activity is paused or the
scan has ended. Theme changes, counts and technical disclosures remain instant.

Keep stable lazy-list keys, the existing insertion fade and RAM-only scroll
restoration. Do not add shared-element navigation, per-scroll arrival effects,
custom body fonts, haptics or dynamic wallpaper colour in this pass.

## Verification

Use the direct build and emulator runner in [BUILDING](../BUILDING.md).
`compose-design` checks semantic/category contrast, actual radar outcomes,
live animation-setting changes and static/off-screen behaviour. Run relevant
existing Compose checks for layout, Back/list position, privacy, reports and
dialogs. Inspect synthetic list, details and sheet captures in both themes,
including 320dp / 200% text and wide layouts. Hardware performance, gestures and
physical TalkBack remain separate manual checks.
