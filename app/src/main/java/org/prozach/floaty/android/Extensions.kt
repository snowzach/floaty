package org.prozach.floaty.android

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.util.*

private var toast: Toast? = null

fun Context.showToast(message: CharSequence?) {
    message?.let {
        toast?.cancel()
        toast = Toast.makeText(this, message, Toast.LENGTH_SHORT).apply { show() }
    }
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

infix fun UShort.shr(bitCount: Int): UShort =
    (this.toUInt() shr bitCount).toUShort()

infix fun UShort.shl(bitCount: Int): UShort =
    (this.toUInt() shl bitCount).toUShort()

fun String.hexToBytes() =
    this.chunked(2).map { it.toUpperCase(Locale.US).toInt(16).toByte() }.toByteArray()

fun ByteArray.toHexString(): String =
    joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

fun UShort.to2UByteArrayInBigEndian(): UByteArray =
    (1 downTo 0).map {
        (this.toInt() shr (it * Byte.SIZE_BITS)).toUByte()
    }.toUByteArray()

fun crc16(input: UByte, polynomial: UShort): UShort {
    val bigEndianInput = input.toUShort() shl 8
    return (0 until 8).fold(bigEndianInput) { result, _ ->
        val isMostSignificantBitOne =
            result and 0x8000.toUShort() != 0.toUShort()
        val shiftedResult = result shl 1
        when (isMostSignificantBitOne) {
            true -> shiftedResult xor polynomial
            false -> shiftedResult
        }
    }
}

val crc16Table = (0 until 256).map {
    crc16(it.toUByte(), 0x1021.toUShort())
}

fun crc16(inputs: UByteArray): UShort {
    return inputs.fold(0.toUShort()) { remainder, byte ->
        val bigEndianInput = byte.toUShort() shl 8
        val index = (bigEndianInput xor remainder) shr 8
        crc16Table[index.toInt()] xor (remainder shl 8)
    }
}

private fun byteArrayOfInts(vararg ints: Int) =
    ByteArray(ints.size) { pos -> ints[pos].toByte() }


