package com.krzykrucz.moneytransfers.adapters

import com.krzykrucz.moneytransfers.domain.transfers.AccountBalance
import com.krzykrucz.moneytransfers.domain.transfers.AccountNumber
import com.krzykrucz.moneytransfers.domain.transfers.AsyncFactory
import com.krzykrucz.moneytransfers.domain.transfers.AsyncOutput
import com.krzykrucz.moneytransfers.domain.transfers.OrdererAccount
import com.krzykrucz.moneytransfers.domain.transfers.TransferLimit
import java.util.concurrent.ConcurrentHashMap

data class PersistentAccount(
        val accountNumber: AccountNumber,
        val balance: AccountBalance,
        val transferLimit: TransferLimit
)

fun OrdererAccount.toPersistentAccount() =
        PersistentAccount(this.accountNumber, this.balance, this.transferLimit)

object Database {
    val accounts: ConcurrentHashMap<String, PersistentAccount> = ConcurrentHashMap()
}

class StoreAccountInMemory : SaveAccount {
    override fun invoke(account: OrdererAccount): AsyncOutput<String, Unit> {
        Database.accounts[account.accountNumber.number.text] = account.toPersistentAccount()
        return AsyncFactory.justSuccess(Unit)
    }

}


