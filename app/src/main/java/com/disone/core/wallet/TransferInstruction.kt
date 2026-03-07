package com.disone.core.wallet

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction

/** System Program ID (11111111111111111111111111111111 = 32 zero bytes in base58) */
private val SYSTEM_PROGRAM_ID = SolanaPublicKey(ByteArray(32))

/** Build a System Program transfer instruction. Instruction index 2 = Transfer. */
fun createTransferInstruction(
    from: SolanaPublicKey,
    to: SolanaPublicKey,
    lamports: Long
): TransactionInstruction {
    // Transfer instruction: [2] + lamports (8 bytes little endian)
    val data = ByteArray(9).apply {
        this[0] = 2 // Transfer instruction index
        for (i in 0..7) {
            this[1 + i] = (lamports shr (i * 8)).toByte()
        }
    }
    val accounts = listOf(
        AccountMeta(from, true, true),  // from: signer, writable
        AccountMeta(to, false, true)    // to: not signer, writable
    )
    return TransactionInstruction(SYSTEM_PROGRAM_ID, accounts, data)
}
