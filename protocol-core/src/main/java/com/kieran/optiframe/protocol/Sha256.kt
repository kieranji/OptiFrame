package com.kieran.optiframe.protocol

import java.security.MessageDigest

object Sha256 {

    fun digest(data: ByteArray): ByteArray {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(data)
    }

    fun hex(data: ByteArray): String {
        return digest(data).joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }
}