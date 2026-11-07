# OptiFrame M3 implementation handoff

Updated: 2026-11-01

## Checkpoint

This snapshot contains the M3 software path implemented in this session. It is not marked acceptance-complete: the current Windows build environment fails during Gradle dependency-catalog generation, and no Android device pair is available for the manual's optical-transfer acceptance runs.

## Included implementation

- P1 SAFE30 optical frame layout, PC-007 control bands and anchors, OF8/RaptorQ file framing, RGB888 rendering/decoding, and SHA-256 verification are present in `protocol-core` and `optional-openrq`.
- The Compose sender has file selection, manifest/bootstrap export, and an initial bootstrap QR screen. The receiver has manifest/bootstrap import, QR scanning, CameraX preview, manual four-anchor perspective calibration, frame decoding, verified file saving, and receiver goodput/macroblock metrics.
- The M3 no-feedback sender mode now supports a per-block fixed repair fraction and automatic advancement after its planned repair queue. The app configures 20% repair overhead, following the manual's no-BLE path. A scheduler test was added for this behavior; it has not been run.
- After a successful receive, the receiver can export a JSON benchmark report with the file digest/result, data-phase goodput, capture utilization, macroblock pass rate, device model, and the manual's P1 SAFE thresholds. Distance and fixed-stand setup are explicitly null because they are not collected by the UI. A JSON-escaping/field test was added; it has not been run.
- Sender selection now copies to a cache file and reads OF8 blocks on demand. Receiver verification writes incrementally to a cache file and checks SHA-256 while streaming to avoid additional whole-file copies.
- OF8 color classification uses the duplicated pilot patches to estimate eight RGB class centers and confidence margins. Low-confidence cells are reported as erasures to the Reed-Solomon inner decoder.
- A file-backed SAFE30/OpenRQ round-trip test was added for streamed output verification; it has not run in this environment.
- BLE feedback is not part of this M3 path; the manual places BLE real-time control with M4.

## Verification state

- A prior continuation note in the supplied progress export reports 128 JVM tests passing and a temporary Android harness successfully assembling the prior SAFE30 display component. Those results predate the Android activity, CameraX receiver, and fixed-repair additions in this snapshot.
- Java 17 and the pinned Gradle 9.5 distribution are now available. `:protocol-core:test :optional-openrq:test :app:assembleDebug` still does not reach source compilation: Gradle fails compiling generated version-catalog accessors with `java.nio.file.AccessDeniedException` while closing a Gradle library JAR on Windows.
- No Android SDK 37 installation or Android device pair was available in the inspected environment. No install, optical transfer, 100 MiB run, or fixed-stand benchmark has been performed.

## M3 acceptance still required by the manual

- Transfer a random 100 MiB file and match SHA-256 in 20 of 20 runs on a reference device pair.
- At 25 cm on a fixed stand, achieve median optical goodput of at least 60 KiB/s and macroblock pass rate of at least 90%.

Until these gates are measured and the build/test tasks pass, M3 cannot be reported as acceptance-complete. The receiver still requires manual four-anchor calibration, and device-specific exposure/timing behavior needs on-device validation.

## M4 work started

- Added a FAST60 presentation scheduler that emits a unique frame at each VSYNC opportunity, while preserving SAFE30's two-presentation behavior.
- Added a CRC32C-protected 18-byte BLE control message codec for block decoded, repair request, pause, resume, cancel, and receiver-complete messages. The manual specifies the Android GATT control-link functions but leaves UUIDs and message bytes unspecified; this implementation defines a versioned service/characteristic profile.
- Added Android BLE GATT sender/peripheral and receiver/central prototypes. M4 bootstraps advertise the FAST60+BLE capability; the sender uses a 10% repair cushion plus receiver feedback for additional repairs instead of M3's fixed 20% no-feedback budget. Frame-sequence gaps trigger receiver repair requests.
- Added explicit PC-007 mixed-frame detection (top/bottom timing or duplicate-header disagreement), a dropped-mixed-frame metric, and a FAST60 report target of 115 KiB/s.
- Added tests for FAST60 frame advancement, control-message round trips and CRC rejection, pause/resume, mixed-frame detection, and FAST60 report fields. They have not run.
- FAST60's P1 payload ceiling is 12 × 256 bytes × 60 unique frames/s = 184,320 bytes/s (180 KiB/s), before losses or repairs. This is derived from the frame capacity, not a device measurement.
- M4 remains unverified. Gradle currently fails before source compilation with a Windows Java `AccessDeniedException` resolving JAR paths through ZipFS. BLE pairing, camera mixed-frame rates, and the P1 FAST median >=115 KiB/s with no file errors require Android hardware testing.

## Resume steps

1. Resolve the Windows Gradle version-catalog `AccessDeniedException` and install Android SDK 37.
2. From the project root, run `gradlew.bat :protocol-core:test :optional-openrq:test :app:assembleDebug` and fix any source/test errors. For M4, verify BLE session pairing/control, pause/resume/cancel, block feedback, FAST60 composite-frame drops, and end-to-end SHA-256.
3. Install on two Android devices, transfer test files, and record receiver SHA-256, goodput, and macroblock pass rate for every run.
4. Complete the two M3 acceptance gates above and attach raw logs/results before marking M3 complete.

No changes were pushed to GitHub. The project was extracted from the supplied progress export into this snapshot.
