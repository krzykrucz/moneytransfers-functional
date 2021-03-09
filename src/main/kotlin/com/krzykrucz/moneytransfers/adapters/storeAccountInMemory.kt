package com.krzykrucz.moneytransfers.adapters

import arrow.core.right
import com.krzykrucz.moneytransfers.domain.transfers.AccountBalance
import com.krzykrucz.moneytransfers.domain.transfers.AccountNumber
import com.krzykrucz.moneytransfers.domain.transfers.OrdererAccount
import com.krzykrucz.moneytransfers.domain.transfers.TransferLimit
import java.util.concurrent.ConcurrentHashMap

private data class PersistentAccount(
        val accountNumber: AccountNumber,
        val balance: AccountBalance,
        val transferLimit: TransferLimit
)

private fun OrdererAccount.toPersistentAccount() =
        PersistentAccount(this.accountNumber, this.balance, this.transferLimit)

private val accounts: ConcurrentHashMap<String, PersistentAccount> = ConcurrentHashMap()

val storeAccountInMemory: SaveAccount = { account ->
    accounts[account.accountNumber.number.text] = account.toPersistentAccount()
    Unit.right()
}
