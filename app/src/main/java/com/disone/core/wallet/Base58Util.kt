package com.disone.core.wallet

import java.math.BigInteger

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

fun String.decodeBase58(): ByteArray {
    if (isEmpty()) return ByteArray(0)
    var num = BigInteger.ZERO
    for (c in this) {
        val idx = ALPHABET.indexOf(c)
        if (idx < 0) throw IllegalArgumentException("Invalid Base58 character: $c")
        num = num * BigInteger.valueOf(58) + BigInteger.valueOf(idx.toLong())
    }
    var bytes = num.toByteArray()
    if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes = bytes.copyOfRange(1, bytes.size)
    var leadingZeros = 0
    for (c in this) {
        if (c != '1') break
        leadingZeros++
    }
    return ByteArray(leadingZeros) { 0 } + bytes
}

fun ByteArray.toBase58(): String {
    if (isEmpty()) return ""
    var num = BigInteger(1, this)
    var result = ""
    while (num > BigInteger.ZERO) {
        val (div, rem) = num.divideAndRemainder(BigInteger.valueOf(58))
        result = ALPHABET[rem.toInt()] + result
        num = div
    }
    for (b in this) {
        if (b != 0.toByte()) break
        result = "1$result"
    }
    return result
}
