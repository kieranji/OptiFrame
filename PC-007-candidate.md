# PC-007 P1 control-band candidate

**Status: CANDIDATE. PC-007 is still unresolved and is not finalized.**
The locations, patterns, and timing encoding below are what the current code
(`P1ControlBandLayout`, `P1TimingRecordCodec`) implements; they may still change.
See `docs/protocol-clarifications.md` for the open clarification.

## Fixed data geometry

- Full logical grid: 160 × 96 cells.
- Data rectangle: `x=16..143`, `y=6..89`.
- Macroblocks: 32 × 28 cells, four columns by three rows.
- Slot numbering: B0..B11, left-to-right then top-to-bottom.

## Proposed control placements

Coordinates are zero-based cell origins. Rectangles include their origin and
exclude `origin + size` at the far edge.

| Structure | Proposed placement | Encoding |
|---|---|---|
| Left global header | origin `(0,40)`, size `16×16` | 31-byte RS matrix, row-major and MSB-first; final 8 cells fixed to `10100101` |
| Right global header | origin `(144,40)`, size `16×16` | Identical copy of left header |
| Top timing strip | row `y=2`, `x=16..143` | 64-bit record repeated twice across the 128 cells |
| Bottom timing strip | row `y=93`, `x=16..143` | Same record as top strip |
| Corner anchors | origins `(5,5)`, `(149,5)`, `(5,85)`, `(149,85)`, each `6×6` | Same 6×6 black/white pattern: black outer ring, white inner ring, black 2×2 centre |
| Left OF8 pilots | origin `(4,24)`, size `8×4` | Eight `2×2` patches, labels 0..7 row-major |
| Right OF8 pilots | origin `(148,24)`, size `8×4` | Duplicate of left pilots using palette labels 0..7 |

Unassigned control cells use palette label 0. The pilots use the fixed V1 OF8
palette (channel levels 32 and 224). The 6×6 anchors stay smaller than a
standard 7×7 QR finder.

## Proposed timing record

The 64-bit record is concatenated MSB-first:

1. Sync byte: `0xD3`.
2. Low 16 bits of `frame_sequence`, big-endian.
3. One parity bit equal to the least-significant bit of `frame_sequence`.
4. A 31-bit m-sequence.
5. CRC-8/ATM over the preceding seven packed bytes.

The m-sequence uses polynomial `x^5 + x^2 + 1`, initial state `0x1F`, emits
the state MSB, then updates with `feedback = bit4 XOR bit1` and
`state = ((state << 1) & 0x1F) | feedback`. It has period 31. The 64-bit record
is duplicated to fill one 128-cell timing row. Top and bottom rows must decode
to identical records; disagreement marks a composite capture.

## Trade-offs and remaining validation

- Centered headers use the complete 16-cell side-band width and keep both
  copies at the same vertical coordinate.
- Repeated 2×2 pilot patches improve spatial averaging while keeping the
  side bands mostly clear.
- A one-row timing record leaves the other reserved rows available for quiet
  spacing and reduces control overhead.
- The design has not been rendered or sampled by a camera. Cell contrast,
  anchor tracking, pilot sample size, and timing-strip reliability still need
  optical experiments before this can become a frozen V1 profile.
- Choosing a different header Y position or timing encoding changes physical
  interoperability and requires a new protocol version or clarification.
