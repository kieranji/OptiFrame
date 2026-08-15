package com.kieran.optiframe.protocol

object ProtocolConstants {

    // -------------------------------------------------------------------------
    // Protocol identity
    // -------------------------------------------------------------------------

    const val PROTOCOL_VERSION: Int = 0x01

    // Bootstrap QR magic: ASCII "OFV1"
    val BOOTSTRAP_MAGIC: ByteArray =
        byteArrayOf(
            0x4F.toByte(),
            0x46.toByte(),
            0x56.toByte(),
            0x31.toByte()
        )

    // Optical frame magic: ASCII "OF"
    val FRAME_MAGIC: ByteArray =
        byteArrayOf(
            0x4F.toByte(),
            0x46.toByte()
        )

    // -------------------------------------------------------------------------
    // Serialization rules
    // -------------------------------------------------------------------------

    const val NETWORK_BYTE_ORDER: String = "BIG_ENDIAN"
    const val BIT_ORDER: String = "MSB_FIRST"

    // -------------------------------------------------------------------------
    // Source symbols
    // -------------------------------------------------------------------------

    const val OF4_SOURCE_SYMBOL_BYTES: Int = 128
    const val OF8_SOURCE_SYMBOL_BYTES: Int = 256

    // -------------------------------------------------------------------------
    // Local macroblock structure
    // -------------------------------------------------------------------------

    const val LOCAL_HEADER_BYTES: Int = 12
    const val CRC32C_BYTES: Int = 4

    // OF4:
    // 12 B local header
    // + 128 B source symbol
    // + 4 B CRC32C
    // = 144 B
    const val OF4_PLAIN_OBJECT_BYTES: Int = 144

    // OF8:
    // 12 B local header
    // + 256 B source symbol
    // + 4 B CRC32C
    // = 272 B
    const val OF8_PLAIN_OBJECT_BYTES: Int = 272

    // -------------------------------------------------------------------------
    // Macroblock geometry
    // -------------------------------------------------------------------------

    const val OF4_MACROBLOCK_WIDTH: Int = 32
    const val OF4_MACROBLOCK_HEIGHT: Int = 22
    const val OF4_MACROBLOCK_CELLS: Int =
        OF4_MACROBLOCK_WIDTH * OF4_MACROBLOCK_HEIGHT

    const val OF8_MACROBLOCK_WIDTH: Int = 32
    const val OF8_MACROBLOCK_HEIGHT: Int = 28
    const val OF8_MACROBLOCK_CELLS: Int =
        OF8_MACROBLOCK_WIDTH * OF8_MACROBLOCK_HEIGHT

    // -------------------------------------------------------------------------
    // Reed-Solomon / GF(256)
    // -------------------------------------------------------------------------

    const val GF256_PRIMITIVE_POLYNOMIAL: Int = 0x11D

    const val RS_PARITY_BYTES: Int = 32

    // OF8 uses two shortened RS(168,136) codewords.
    const val OF8_RS_DATA_BYTES: Int = 136
    const val OF8_RS_CODEWORD_BYTES: Int = 168

    // Two OF8 codewords after interleaving:
    // 168 * 2 = 336 B
    const val OF8_RS_INTERLEAVED_BYTES: Int =
        OF8_RS_CODEWORD_BYTES * 2

    // OF4 uses shortened RS(176,144).
    const val OF4_RS_DATA_BYTES: Int = 144
    const val OF4_RS_CODEWORD_BYTES: Int = 176

    // -------------------------------------------------------------------------
    // Checksums
    // -------------------------------------------------------------------------

    // CRC32C / Castagnoli
    const val CRC32C_POLYNOMIAL: Long = 0x1EDC6F41L
    const val CRC32C_REFLECTED_POLYNOMIAL: Long = 0x82F63B78L

    // Header CRC-8/ATM
    const val CRC8_ATM_POLYNOMIAL: Int = 0x07

    // -------------------------------------------------------------------------
    // Profiles
    // -------------------------------------------------------------------------

    const val PROFILE_P0_ID: Int = 0
    const val PROFILE_P1_ID: Int = 1
    const val PROFILE_P2_ID: Int = 2

    // P0 / OF4
    const val P0_GRID_WIDTH: Int = 128
    const val P0_GRID_HEIGHT: Int = 80
    const val P0_MACROBLOCK_COUNT: Int = 6
    const val P0_PAYLOAD_BYTES_PER_FRAME: Int = 768

    // P1 / OF8
    const val P1_GRID_WIDTH: Int = 160
    const val P1_GRID_HEIGHT: Int = 96
    const val P1_MACROBLOCK_COUNT: Int = 12
    const val P1_PAYLOAD_BYTES_PER_FRAME: Int = 3072

    // P2 / OF8
    const val P2_GRID_WIDTH: Int = 224
    const val P2_GRID_HEIGHT: Int = 128
    const val P2_MACROBLOCK_COUNT: Int = 24
    const val P2_PAYLOAD_BYTES_PER_FRAME: Int = 6144

    // -------------------------------------------------------------------------
    // OF8 initial palette
    // -------------------------------------------------------------------------

    const val OF8_PALETTE_LOW: Int = 32
    const val OF8_PALETTE_HIGH: Int = 224

    // -------------------------------------------------------------------------
    // Global header
    // -------------------------------------------------------------------------

    const val GLOBAL_HEADER_DATA_BYTES: Int = 19
    const val GLOBAL_HEADER_RS_BYTES: Int = 31

    // -------------------------------------------------------------------------
    // RaptorQ
    // -------------------------------------------------------------------------

    const val MAX_SOURCE_SYMBOLS_PER_BLOCK: Int = 1024
}