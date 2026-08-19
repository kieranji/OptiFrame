## PC-007 — P1 control-band physical layout

Status: OPEN
Compatibility impact: HIGH
Introduced: Stage 13C-1

### Purpose

Freeze the physical cell layout of all P1 control structures outside
the canonical 128×84 OF8 data region.

No renderer or receiver implementation may treat currently unresolved
coordinates or patterns as V1 protocol constants.

---

### A. Fixed by Draft 0.1

P1 full logical grid:

- width = 160 cells
- height = 96 cells

P1 OF8 data region:

- width = 128 cells
- height = 84 cells
- 4 macroblock columns
- 3 macroblock rows
- 12 macroblocks total
- each macroblock = 32×28 cells

Therefore the current canonical data-region geometry is:

- data x = 16..143
- data y = 6..89

Reserved space therefore consists of:

- left side band: x = 0..15
- right side band: x = 144..159
- top reserved band over data region: y = 0..5
- bottom reserved band over data region: y = 90..95

These dimensions follow directly from the Draft 0.1 P1 dimensions.

---

### B. Global Header — partially fixed

Draft 0.1 requires:

- two identical Global Headers per optical frame
- one in the left reserved band
- one in the right reserved band
- each header is 16×16 cells
- black/white only
- either copy may be sufficient for decoding

The 16-cell header width exactly matches the derived 16-cell P1 side-band
width.

UNRESOLVED:

- exact Y coordinate of the left header
- exact Y coordinate of the right header
- whether both copies must have the same Y coordinate

Do not freeze these values yet.

---

### C. Anchors — unresolved geometry

Draft 0.1 requires:

- four continuously present corner anchors
- anchors smaller than a standard QR finder
- previous-frame tracking is preferred after initial lock

UNRESOLVED:

- anchor width
- anchor height
- exact cell coordinates
- internal black/white pattern
- required quiet/separation cells
- whether the four anchors are identical or orientation-specific

No anchor geometry is currently part of the frozen V1 protocol.

---

### D. Continuous color pilots — unresolved geometry

Draft 0.1 requires:

- pilots on both left and right sides
- pilots repeated every frame
- eight known OF8 color classes
- receiver uses them to update the color model

UNRESOLVED:

- patch width and height
- number of cells per color
- ordering of the eight colors
- left/right duplication rule
- spacing between patches
- exact coordinates
- whether pilot statistics use all cells or central samples only

No pilot geometry is currently frozen.

---

### E. Top/bottom timing structures — unresolved encoding

Draft 0.1 requires both top and bottom timing structures to carry:

- a fixed sequence
- frame parity
- low frame-sequence information

Receiver uses disagreement between the top and bottom timing structures
as evidence of a composite capture.

UNRESOLVED:

- exact occupied rows within the 6-cell reserved band
- sequence length
- m-sequence polynomial
- initial state
- bit ordering
- frame-sequence low-bit count
- parity encoding
- repetition / redundancy
- orientation
- black/white versus OF8 representation
- synchronization delimiter pattern

No timing bitstream is currently frozen.

---

### F. Explicitly NOT adopted for V1

A RescQR-style full dedicated border with frame-ID / color-reference
squares and Viterbi mixture separation is not part of the current V1
baseline.

It remains a V1.1 research direction.

---

### G. Current implementation rule

Until PC-007 is CLOSED:

- the 128×84 P1 data region is canonical
- occupied OF8 data cells may be composed into the 160×96 canvas
- unoccupied data slots remain DATA_UNUSED
- all cells outside the data region remain CONTROL_RESERVED
- renderer code must not invent final control patterns
- receiver code must not depend on provisional control coordinates