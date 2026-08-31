# V0.4 UI freeze

The V0.4 primary screen is intentionally minimal and contains:

- App title and private/offline subtitle
- Ready or Recording status card
- Transcript card with word-count chip and scrollable transcript area
- Record/Stop primary action
- Save action
- One compact Advanced entry

## States

Ready uses a green 56 dp LED, the `Ready` label, and `English • fully offline`.
Recording uses a red 56 dp LED, the `Recording` label, the real microphone
level meter, a single-line `MM:SS` timer, and `16 kHz • microphone active`.
The meter is hidden when idle and is not the only recording-state indication.

## Advanced

Advanced opens as a modal bottom sheet and groups secondary functionality into
FILE, TRANSCRIPTION, TESTING, and APP sections. It contains Import WAV, clear
with confirmation, engine selection, technical hotwords, same-WAV comparison,
Retest, benchmark details, and About. The primary layout does not expand when
Advanced is opened.

About identifies Pradeep Speech2Text, its developer and engineering focus,
local-only privacy behavior, app version, Moonshine/Zipformer, and sherpa-onnx.

## Responsive requirements

The status timer must remain a single-line `MM:SS` value at all supported font
scales. Status content must allocate space for LED, labels, meter, and timer
without overlap or clipping. Transcript text remains left-aligned and
scrollable when present. Advanced content scrolls on smaller screens and all
interactive controls retain approximately 48 dp touch targets.

The V0.4 primary UI is frozen. Future work should not modify the primary layout
or visual hierarchy unless required to correct a demonstrated
usability/accessibility defect.

This freeze does not prevent new functionality inside Advanced or future
settings screens.
