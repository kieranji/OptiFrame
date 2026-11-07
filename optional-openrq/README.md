# Optional OpenRQ backend

This module isolates OpenRQ from `protocol-core`. The core remains dependency-free and accepts backend factories through its sender and receiver sessions.

The root `settings.gradle.kts` includes `:optional-openrq` and declares a narrowly scoped Ivy repository for the official OpenRQ 3.3.2 release asset. The upstream JAR was fetched for local compilation and its SHA-256 was recorded as `904603c0c6a031090c94d7b82dc4e7e1426f4968ac0dde94432ae3352cf106b8`; the binary is not committed.

Typical use:

```kotlin
val sender = OpenRqRaptorQ.senderSession(
    fileName = name,
    fileBytes = bytes,
    initialRepairSymbolsPerBlock = 2
)

val receiver = OpenRqRaptorQ.receiverSession(sender.manifest)
```

## M3 P1 SAFE30 path

The M3 wrappers connect a file session to the P1 frame codec and SAFE30
two-refresh presentation policy. The canonical B0..B11 data-slot mapping
comes from figure 9-1 in the V1 manual. PC-007 remains unresolved; the candidate
coordinates and control patterns are defined in `P1ControlBandLayout` and
`P1TimingRecordCodec`, then applied by `P1PhysicalFrameRenderer`; its
`P1PhysicalFrameRasterizer` converts the result to row-major RGB888 pixels
using the fixed OF8 levels. The caller still supplies protocol-specific
global-header and flag values.

For a perspective-corrected one-pixel-per-cell RGB888 capture, the JVM core's
`P1Rgb888GridDecoder` classifies OF8 labels, checks both header copies and the
top/bottom timing records, reconstructs occupied data slots, and returns per-
cell confidence for `P1M3ReceiverSession`. Camera homography and area sampling
must run before this grid decoder.
Use the same uint32 session ID carried by the bootstrap QR in both sessions;
the header factory must place it into every generated global header.

```kotlin
import com.kieran.optiframe.ui.optical.P1Safe30Display

val orderedP1Positions = P1SlotPosition.canonicalP1()

val sender = OpenRqRaptorQ.m3SenderSession(
    fileName = name,
    fileBytes = bytes,
    initialRepairSymbolsPerBlock = 2,
    sessionId = sessionIdFromBootstrapQr,
    firstFrameSequence = 0,
    positions = orderedP1Positions,
    flagsForItem = { item -> flagsFor(item) },
    headerFactory = { block, sequence, kind ->
        makeP1Header(block, sequence, kind)
    }
)

val receiver = OpenRqRaptorQ.m3ReceiverSession(
    manifest = sender.manifest,
    expectedSessionId = sessionIdFromBootstrapQr,
    positions = orderedP1Positions
)

// In the Android app, this composable advances the sender from the frame clock,
// displays each SAFE30 frame twice, and holds the last frame while waiting.
P1Safe30Display(session = sender)

// A custom renderer can call once per refresh; physicalFrame is 160×96 labels.
val presentation = sender.nextVsyncPresentation()
val fullLogicalGrid = presentation?.physicalFrame

// On the receiver, process a captured logical frame and return feedback
// through the existing control channel when present.
val received = receiver.acceptVsyncFrame(capturedFrame)
received.feedback?.let(sender::applyFeedback)

// After the receiver has all blocks, verify the final file and inspect M3 metrics.
val originalBytes = receiver.finishAndVerifyFile()
val metrics = receiver.metrics
```

When the sender returns `null`, it is waiting for feedback or has finished;
`P1Safe30Display` keeps the last frame visible. The composable advances on the
Compose frame clock; its timing still needs validation on Android devices. The
receiver checks every frame's session ID against the bootstrap session and uses
the same canonical slot mapping. A full camera pipeline and control-channel
transport are outside this core integration.

By default, each newly observed incomplete logical frame requests two more
repair symbols (about 16.7% of a full 12-slot P1 frame). Re-capturing the same
SAFE30 frame does not enqueue that repair request a second time. A frame whose
all slots fail still carries its block ID and can request repairs. Pass
`additionalRepairSymbolsIfIncomplete = 0` to disable automatic repair asks.

The receiver metrics include captured and unique frame counts, duplicate
capture rate, macroblock pass rate, unique symbols, SHA-256 verification,
elapsed transfer time, and verified-file goodput. The timer starts at the
first frame carrying the expected session ID and stops after SHA-256 succeeds.
The manual's P1 SAFE target is median goodput of at least 60 KiB/s and a
macroblock pass rate of at least 90% at 25 cm on a fixed stand; one transfer's
metrics alone do not establish the required median.

OpenRQ's 3.3.2 release is from 2015. The project says it is no longer actively developed and documents decoding performance limitations and missing packet interleaving/sub-block support. Review those constraints before production use.

The core and optional OpenRQ sources compile in a minimal Gradle harness on
JDK 17; 126 tests pass, including actual RaptorQ repair and P1 SAFE30 file
recovery. The repository Gradle invocation stops before compilation while
generating version-catalog accessors because Windows denies access to an
internal Gradle JAR. Android integration and hardware behavior remain
unverified.
