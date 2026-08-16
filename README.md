# OptiFrame

**OptiFrame V1** is an experimental Android screen-to-camera optical file transfer system.

The project aims to transfer arbitrary files between two Android devices by displaying a rapidly changing colored optical matrix on the sender's screen and capturing it with the receiver's camera.

The core requirement is **bit-perfect file recovery**:

> The received file must have the same length and SHA-256 digest as the original file.

OptiFrame does **not** compress, transcode, or reinterpret the original file contents. Files are treated as raw byte arrays from end to end.

---

## Project Status

**Status:** Research prototype / protocol implementation  
**Protocol baseline:** OptiFrame V1 Technical Manual, Draft 0.1  
**Protocol baseline date:** 2026-08-10  
**Current development focus:** Pure Kotlin/JVM `protocol-core`

The project is currently implementing and validating the protocol entirely in memory before introducing screen rendering or camera capture.

Current principle:

```text
bytes
  ↓
protocol encoding
  ↓
logical optical matrix
  ↓
protocol decoding
  ↓
bytes
  ↓
SHA-256 verification
```

CameraX, computer vision, real display timing, BLE control and RaptorQ integration will only be introduced after the in-memory protocol path is verified.

---

## Core Goals

OptiFrame V1 is designed around the following goals:

- Transfer arbitrary binary files.
- Preserve the original file exactly.
- Require no media transcoding.
- Require no file compression.
- Operate offline over a screen-to-camera optical link.
- Support multiple robustness profiles.
- Detect corrupted data before accepting it.
- Verify the final reconstructed file using SHA-256.
- Produce measurable and reproducible performance results.
- Keep protocol fields, constants and serialization rules centralized.

The V1 architecture separates frame-local recovery from cross-frame recovery:

```text
CRC32C
   ↓
Reed–Solomon
   ↓
RaptorQ
   ↓
SHA-256
```

CRC32C and Reed–Solomon protect individual optical macroblocks.

RaptorQ is planned to recover missing macroblocks or frames.

SHA-256 is the final bit-perfect integrity check.

---

## Terminology

OptiFrame data frames are referred to as:

**OptiFrame optical matrix frames**

or:

**彩色光学矩阵帧**

They must not be described as a new QR Code format.

Standard QR Code is used only for session bootstrap and initial discovery.

---

# Architecture

The planned V1 project structure is:

```text
OptiFrame/
│
├── app/
│   └── Android application and Compose UI
│
├── protocol-core/
│   └── Pure Kotlin/JVM protocol implementation
│
├── renderer/
│   └── Optical frame rendering
│
├── receiver/
│   └── Camera capture and decoding pipeline
│
├── benchmark/
│   └── Performance and reproducibility tooling
│
└── README.md
```

The final Android implementation is expected to expand into components for:

```text
App UI
Protocol Core
FEC
Sender Renderer
Camera Capture
Vision
Color Classifier
Control Link
Benchmarking
```

The current implementation intentionally focuses on `protocol-core` first.

---

# Android Project Configuration

Current Android project configuration:

```text
Application ID:
com.kieran.optiframe

minSdk:
26

compileSdk:
37

targetSdk:
36

JVM:
17

Language:
Kotlin

Build system:
Gradle Kotlin DSL

UI:
Jetpack Compose
```

`protocol-core` is a pure Kotlin/JVM library and must not depend on:

```text
android.*
androidx.*
CameraX
Compose
OpenCV
```

This allows the protocol implementation to be tested directly on the JVM without an Android device or emulator.

---

# V1 Protocol Rules

## Byte order

All multi-byte integers use:

```text
Network byte order
=
Big-endian
```

## Bit order

Bits inside serialized bytes are processed:

```text
MSB-first
```

## File handling

The sender must not alter file contents based on MIME type or extension.

Examples:

```text
JPG
MP4
PDF
APK
ZIP
unknown binary data
```

are all handled as raw byte arrays.

---

# OF8 Macroblock

The first implemented optical data format is OF8.

Each OF8 macroblock carries a 256-byte source symbol.

The unprotected macroblock object is:

```text
Offset   Size    Field
--------------------------------
0        4 B     ESI
4        1 B     slot_index
5        1 B     flags
6        2 B     frame_seq_low
8        2 B     symbol_length
10       2 B     reserved
12       256 B   payload
268      4 B     CRC32C
--------------------------------
Total    272 B
```

CRC32C covers the first 268 bytes.

For V1:

```text
symbol_length = 256
reserved      = 0
```

---

# OF8 Encoding Pipeline

The target V1 OF8 macroblock encoding pipeline is:

```text
256 B source symbol
        ↓
12 B local header
        ↓
CRC32C
        ↓
272 B macroblock object
        ↓
Whitening
        ↓
272 B whitened object
        ↓
Split into:
136 B + 136 B
        ↓
Shortened RS(168,136) × 2
        ↓
168 B + 168 B
        ↓
Byte interleaving
        ↓
336 B
        ↓
MSB-first bit expansion
        ↓
2688 bits
        ↓
3 bits / symbol
        ↓
896 OF8 symbols
        ↓
Spatial permutation
        ↓
32 × 28 logical matrix
        ↓
OF8 RGB palette
```

The corresponding reverse path reconstructs the original 272-byte macroblock before CRC validation and payload extraction.

---

# Reed–Solomon

OF8 uses two shortened Reed–Solomon codewords.

Mother code:

```text
RS(255,223)
```

Parameters:

```text
GF(256)

Primitive polynomial:
0x11D

Parity:
32 bytes
```

Shortened code:

```text
RS(168,136)
```

Encoding:

```text
136 B data
      ↓
prepend 87 virtual zero bytes
      ↓
223 B
      ↓
RS(255,223)
      ↓
255 B
      ↓
remove first 87 bytes
      ↓
168 B
```

The final V1 decoder must support both unknown errors and known erasures.

Required correction condition:

```text
2e + s ≤ 32
```

where:

```text
e = unknown errors
s = known erasures
```

---

# Whitening

Whitening prevents large uniform areas caused by source data such as long runs of:

```text
0x00
0xFF
```

Whitening is reversible and does not compress or encrypt the data.

The transformation is:

```text
whitened[i] =
plain[i] XOR PRBS[i]
```

V1 uses SplitMix64 as the pseudorandom stream generator.

Because the Draft 0.1 manual did not completely define `hash64(...)` used for seed derivation, the implementation currently applies the following protocol clarification.

## PC-001 — Whitening Seed Derivation

Status:

```text
Protocol clarification
```

Seed input:

```text
session_id       uint32 BE
frame_sequence   uint32 BE
slot_index       uint8
domain            ASCII "OF"
```

Result:

```text
SHA256(seed_input)
```

The first 8 digest bytes, interpreted as an unsigned 64-bit big-endian integer, form the initial SplitMix64 state.

Each SplitMix64 output word is converted to whitening bytes in little-endian order.

This clarification must be preserved across Kotlin and future C++ implementations.

---

# OF8 Spatial Permutation

After conversion to 896 three-bit symbols, symbols are spatially dispersed using a reversible affine permutation:

```text
π(i) = (a × i + b) mod 896
```

with:

```text
gcd(a, 896) = 1
```

Inverse:

```text
π⁻¹(j) =
a⁻¹ × (j - b) mod 896
```

The original Draft 0.1 described a fixed table for `a` and a hash-derived `b` but did not fully specify either derivation.

The implementation therefore records an explicit clarification.

## PC-002 — Spatial Permutation Derivation

Permutation size:

```text
N = 896
```

Parameter input:

```text
frame_sequence   uint32 BE
slot_index       uint8
domain            ASCII "PI"
```

The input is hashed with SHA-256.

`a` is selected from a fixed set of values coprime with 896.

`b` is deterministically derived from the same digest modulo 896.

The mapping must be exactly reproducible across Kotlin and C++ implementations.

---

# OF8 Color Palette

OF8 carries:

```text
3 bits / cell
```

using eight RGB cube vertices.

Channel levels:

```text
LOW  = 32
HIGH = 224
```

Label mapping:

```text
bit 2 → R
bit 1 → G
bit 0 → B
```

Therefore:

```text
000 → RGB( 32,  32,  32)
001 → RGB( 32,  32, 224)
010 → RGB( 32, 224,  32)
011 → RGB( 32, 224, 224)

100 → RGB(224,  32,  32)
101 → RGB(224,  32, 224)
110 → RGB(224, 224,  32)
111 → RGB(224, 224, 224)
```

The values 32 and 224 are intentionally used instead of 0 and 255 to reduce clipping and extreme display/camera nonlinearities.

The current `protocol-core` uses exact RGB values only for deterministic in-memory tests.

The real camera receiver will instead classify sampled YUV/RGB observations statistically and produce:

```text
symbol
+
confidence
```

Low-confidence observations will eventually be converted into Reed–Solomon erasures.

---

# Current Implementation

The following components are currently implemented or under active validation in `protocol-core`:

```text
ProtocolConstants
BinaryIo

CRC32C
SHA-256

OF8 local macroblock header
OF8 macroblock serialization
OF8 macroblock CRC validation

SplitMix64
Whitening
PC-001 seed derivation

GF(256)
RS(255,223) encoder
Shortened RS(168,136) encoder

Byte interleaving

MSB-first bit serialization
3-bit OF8 symbol conversion

PC-002 affine spatial permutation
32 × 28 logical matrix representation

OF8 RGB palette
```

The current major protocol milestone is:

```text
OF8 noiseless in-memory round trip
```

The next major task is completing the Reed–Solomon decoder with error and erasure correction.

---

# Tests

The project uses JVM unit tests for protocol validation.

Run:

```powershell
.\gradlew.bat :protocol-core:test
```

Full Android build:

```powershell
.\gradlew.bat build
```

Expected result:

```text
BUILD SUCCESSFUL
```

Important test categories include:

```text
Big-endian field serialization
CRC32C known vectors
SHA-256 known vectors
SplitMix64 reproducibility
Whitening reversibility
GF(256) arithmetic
RS generator / parity validation
RS shortening
Byte interleaving
MSB-first bit conversion
OF8 symbol conversion
Spatial permutation inversion
OF8 palette round trip
Macroblock CRC rejection
OF8 matrix round trip
```

The V1 protocol baseline additionally requires future tests for:

```text
RS errors
RS erasures
RS errors + erasures
Full OF8 color round trip
RaptorQ loss recovery
SAFE30 duplicate-frame handling
Whole-file SHA-256 round trips
```

Required file-size tests include:

```text
0 B
1 B
255 B
256 B
257 B
1 MiB
```

---

# Protocol Integrity

Several integrity mechanisms serve different purposes.

## CRC32C

Used for fast detection of corrupted macroblocks.

```text
Polynomial:
0x1EDC6F41

Reflected:
0x82F63B78
```

CRC32C is not a cryptographic hash.

## Reed–Solomon

Used to repair local optical errors and known erasures.

## RaptorQ

Planned for recovery from lost source symbols, macroblocks and optical frames.

## SHA-256

The final reconstructed file is accepted only when its SHA-256 digest matches the manifest.

A successful Reed–Solomon decode alone does not mean that a file transfer succeeded.

---

# Planned Receiver Pipeline

The future Android receiver is expected to follow:

```text
Camera capture
      ↓
Geometry tracking / relocation
      ↓
Timing-strip validation
      ↓
Global header
      ↓
Cell sampling
      ↓
Color classification
      ↓
Symbol confidence
      ↓
Inverse spatial permutation
      ↓
Byte reconstruction
      ↓
RS errors / erasures
      ↓
CRC32C
      ↓
RaptorQ
      ↓
File reconstruction
      ↓
SHA-256
```

The high-performance receiver will eventually require direct YUV processing, bounded frame queues and careful control of exposure and white balance.

---

# Planned Sender Pipeline

The sender will eventually provide:

```text
File selection
      ↓
Manifest
      ↓
Bootstrap QR
      ↓
Geometry calibration
      ↓
Color calibration
      ↓
Profile probe
      ↓
RaptorQ symbols
      ↓
OptiFrame encoding
      ↓
Full-screen optical rendering
```

The renderer is expected to use a low-overhead Android rendering path with nearest-neighbor cell rendering and VSYNC-aligned presentation.

---

# V1 Profiles

The protocol baseline defines:

```text
P0 / OF4
128 × 80
6 macroblocks
128 B / macroblock
768 B payload / optical frame
```

```text
P1 / OF8
160 × 96
12 macroblocks
256 B / macroblock
3072 B payload / optical frame
```

```text
P2 / OF8
224 × 128
24 macroblocks
256 B / macroblock
6144 B payload / optical frame
```

P1 OF8 SAFE30 is the intended first primary OF8 profile.

P2 remains an experimental high-density profile.

---

# Timing Modes

V1 defines two logical timing modes.

## SAFE30

On a 60 Hz display, the same logical optical frame is shown for two VSYNC periods.

Nominal unique logical frame rate:

```text
30 fps
```

SAFE30 is the required first real-device validation mode.

## FAST60

A new logical optical frame is displayed every VSYNC.

Nominal unique logical frame rate:

```text
60 fps
```

FAST60 will only be enabled after SAFE30 achieves sufficiently reliable macroblock decoding and acceptable composite-frame rates.

---

# Performance Claims

**No real-world OptiFrame V1 performance claims are currently made.**

The protocol manual contains engineering targets for later validation, but these numbers are not measured results.

Performance results will only be published after testing across multiple device pairs with:

```text
random incompressible files
multiple distances
multiple viewing angles
different lighting conditions
fixed screen brightness
thermal stability runs
repeated trials
```

Reported metrics should include:

```text
optical goodput
session throughput
capture utilization
macroblock pass rate
RS corrections
RS erasures
RaptorQ overhead
composite-frame rate
p50 / p95 / p99 latency
thermal behavior
SHA-256 integrity
```

---

# Development Roadmap

Current development order:

```text
Phase 1
Pure Kotlin/JVM protocol-core
```

```text
Phase 2
Complete OF8 in-memory encode/decode
```

```text
Phase 3
RS errors + erasures
```

```text
Phase 4
File chunking and manifest
```

```text
Phase 5
RaptorQ integration
```

```text
Phase 6
Android optical renderer
```

```text
Phase 7
CameraX capture baseline
```

```text
Phase 8
Geometry and cell sampling
```

```text
Phase 9
OF4 / OF8 color calibration and classification
```

```text
Phase 10
P1 SAFE30 end-to-end transfer
```

```text
Phase 11
BLE feedback and FAST60
```

```text
Phase 12
Performance optimization and P2 experiments
```

---

# V1 Research Milestones

The protocol baseline ultimately defines the following high-level progression:

```text
M0
Standard QR stream baseline

M1
Custom monochrome optical frame

M2
OF4 + RS erasures

M3
OF8 P1 SAFE + RaptorQ

M4
BLE + FAST60

M5
P2 + native optimization

M6
V1.1 research extensions
```

V1.1 research may later investigate:

```text
soft-decision LDPC
non-binary LDPC
adaptive palettes
mixture-frame recovery
learning-based color classification
higher-order visual modulation
```

These are explicitly outside the current V1 implementation baseline.

---

# Safety

Rapid high-contrast screen changes may be uncomfortable or unsafe for some users.

The final application must include:

```text
flashing-content warning
immediate stop control
SAFE30 mode
brightness control
thermal monitoring
```

OptiFrame is intended for consensual, visible, short-range optical transfer.

---

# License

A project license has not yet been finalized.

---

# Repository

OptiFrame is under active development.

The implementation is currently focused on correctness and reproducibility before performance optimization.

The most important rule for V1 development is:

> First prove that an in-memory OptiFrame matrix can reconstruct the original bytes exactly. Only then connect the protocol to the screen and camera.
