package com.disone.core.wallet

import java.math.BigInteger

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

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
