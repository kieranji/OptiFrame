package com.kieran.optiframe.protocol

/** M4 BLE control messages. Multi-byte integers use network byte order. */
sealed interface P1ControlMessage {
    val sessionId: Long

    data class BlockDecoded(
        override val sessionId: Long,
        val sourceBlockNumber: Int,
        val uniqueSymbolsReceived: Int
    ) : P1ControlMessage

    data class RepairRequested(
        override val sessionId: Long,
        val sourceBlockNumber: Int,
        val additionalRepairSymbols: Int,
        val uniqueSymbolsReceived: Int
    ) : P1ControlMessage

    data class Command(
        override val sessionId: Long,
        val action: Action
    ) : P1ControlMessage

    enum class Action { PAUSE, RESUME, CANCEL, TRANSFER_COMPLETE }
}

/**
 * Compact, CRC-protected wire format for the M4 GATT control characteristic.
 * The manual names the GATT control-link functions but does not assign UUIDs or
 * a byte layout; this codec defines version 1 for this implementation.
 */
object P1ControlMessageCodec {
    const val ENCODED_BYTES = 18
    private const val VERSION = 1
    private const val TYPE_BLOCK_DECODED = 1
    private const val TYPE_REPAIR_REQUEST = 2
    private const val TYPE_PAUSE = 3
    private const val TYPE_RESUME = 4
    private const val TYPE_CANCEL = 5
    private const val TYPE_TRANSFER_COMPLETE = 6

    fun encode(message: P1ControlMessage): ByteArray {
        require(message.sessionId in 0..0xFFFF_FFFFL) { "Session ID must fit uint32" }
        val bytes = ByteArray(ENCODED_BYTES)
        bytes[0] = 0x4F
        bytes[1] = 0x46
        bytes[2] = VERSION.toByte()
        putUnsigned(bytes, 4, message.sessionId, 4)
        when (message) {
            is P1ControlMessage.BlockDecoded -> {
                validateBlock(message.sourceBlockNumber)
                require(message.uniqueSymbolsReceived in 0..0xFFFF)
                bytes[3] = TYPE_BLOCK_DECODED.toByte()
                putUnsigned(bytes, 8, message.sourceBlockNumber.toLong(), 2)
                putUnsigned(bytes, 12, message.uniqueSymbolsReceived.toLong(), 2)
            }
            is P1ControlMessage.RepairRequested -> {
                validateBlock(message.sourceBlockNumber)
                require(message.additionalRepairSymbols in 1..0xFFFF)
                require(message.uniqueSymbolsReceived in 0..0xFFFF)
                bytes[3] = TYPE_REPAIR_REQUEST.toByte()
                putUnsigned(bytes, 8, message.sourceBlockNumber.toLong(), 2)
                putUnsigned(bytes, 10, message.additionalRepairSymbols.toLong(), 2)
                putUnsigned(bytes, 12, message.uniqueSymbolsReceived.toLong(), 2)
            }
            is P1ControlMessage.Command -> {
                bytes[3] = when (message.action) {
                    P1ControlMessage.Action.PAUSE -> TYPE_PAUSE
                    P1ControlMessage.Action.RESUME -> TYPE_RESUME
                    P1ControlMessage.Action.CANCEL -> TYPE_CANCEL
                    P1ControlMessage.Action.TRANSFER_COMPLETE -> TYPE_TRANSFER_COMPLETE
                }.toByte()
            }
        }
        putUnsigned(bytes, 14, Crc32c.compute(bytes, 0, 14), 4)
        return bytes
    }

    fun decode(bytes: ByteArray): P1ControlMessage {
        require(bytes.size == ENCODED_BYTES) { "M4 control message must be $ENCODED_BYTES bytes" }
        require((bytes[0].toInt() and 0xFF) == 0x4F && (bytes[1].toInt() and 0xFF) == 0x46) {
            "Invalid M4 control message magic"
        }
        require((bytes[2].toInt() and 0xFF) == VERSION) { "Unsupported M4 control version" }
        require(readUnsigned(bytes, 14, 4) == Crc32c.compute(bytes, 0, 14)) {
            "M4 control message CRC32C mismatch"
        }
        val sessionId = readUnsigned(bytes, 4, 4)
        val block = readUnsigned(bytes, 8, 2).toInt()
        val repairs = readUnsigned(bytes, 10, 2).toInt()
        val unique = readUnsigned(bytes, 12, 2).toInt()
        return when (bytes[3].toInt() and 0xFF) {
            TYPE_BLOCK_DECODED -> {
                require(repairs == 0)
                P1ControlMessage.BlockDecoded(sessionId, block, unique)
            }
            TYPE_REPAIR_REQUEST -> {
                require(repairs > 0)
                P1ControlMessage.RepairRequested(sessionId, block, repairs, unique)
            }
            TYPE_PAUSE, TYPE_RESUME, TYPE_CANCEL, TYPE_TRANSFER_COMPLETE -> {
                require(block == 0 && repairs == 0 && unique == 0)
                val action = when (bytes[3].toInt() and 0xFF) {
                    TYPE_PAUSE -> P1ControlMessage.Action.PAUSE
                    TYPE_RESUME -> P1ControlMessage.Action.RESUME
                    TYPE_CANCEL -> P1ControlMessage.Action.CANCEL
                    else -> P1ControlMessage.Action.TRANSFER_COMPLETE
                }
                P1ControlMessage.Command(sessionId, action)
            }
            else -> throw IllegalArgumentException("Unknown M4 control message type")
        }
    }

    fun toFeedback(message: P1ControlMessage): RaptorQBlockFeedback? = when (message) {
        is P1ControlMessage.BlockDecoded -> RaptorQBlockFeedback(
            message.sourceBlockNumber, blockDecoded = true,
            uniqueSymbolsReceived = message.uniqueSymbolsReceived
        )
        is P1ControlMessage.RepairRequested -> RaptorQBlockFeedback(
            message.sourceBlockNumber, blockDecoded = false,
            additionalRepairSymbols = message.additionalRepairSymbols,
            uniqueSymbolsReceived = message.uniqueSymbolsReceived
        )
        is P1ControlMessage.Command -> null
    }

    private fun validateBlock(blockNumber: Int) {
        require(blockNumber in 0..0xFFFF) { "Source block number must fit uint16" }
    }

    private fun putUnsigned(target: ByteArray, offset: Int, value: Long, byteCount: Int) {
        for (index in 0 until byteCount) {
            target[offset + byteCount - index - 1] = (value ushr (index * 8)).toByte()
        }
    }

    private fun readUnsigned(source: ByteArray, offset: Int, byteCount: Int): Long {
        var value = 0L
        for (index in 0 until byteCount) {
            value = (value shl 8) or (source[offset + index].toLong() and 0xFF)
        }
        return value
    }
}
