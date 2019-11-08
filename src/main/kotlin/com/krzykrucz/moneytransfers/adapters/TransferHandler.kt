package com.krzykrucz.moneytransfers.adapters

import arrow.core.Either
import arrow.core.flatMap
import com.krzykrucz.moneytransfers.domain.transfers.AccountDebited
import com.krzykrucz.moneytransfers.domain.transfers.AccountNumber
import com.krzykrucz.moneytransfers.domain.transfers.NonEmptyText
import com.krzykrucz.moneytransfers.domain.transfers.OrderTransfer
import com.krzykrucz.moneytransfers.domain.transfers.TransferMoneyError
import com.krzykrucz.moneytransfers.domain.transfers.TransferOrderCheque
import com.krzykrucz.moneytransfers.domain.transfers.flatMapSuccess
import com.krzykrucz.moneytransfers.domain.transfers.mapError
import com.krzykrucz.moneytransfers.domain.transfers.mapSuccess

val transferOrderCheque = TransferOrderCheque(
        "John Smith", "10000000000000000000000000",
        "USD", "10",
        "John Newman", "20000000000000000000000000",
        "Some kind of a transfer"
)

fun TransferMoneyError.toText(): String = this.javaClass.simpleName

class TransferHandler(val loadAccount: LoadAccount,
                      val saveAccount: SaveAccount,
                      val orderTransfer: OrderTransfer,
                      val publishEvents: PublishEvents) {

    fun handle(requestBody: String): Either<String, Unit> {
//        TODO("deserialize")

        return NonEmptyText.of(transferOrderCheque.ordererAccountNumber)
                .flatMap(AccountNumber.Companion::create)
                .toEither { "Cannot deserialize" }
                .flatMap {
                    loadAccount(it)
                            .flatMapSuccess {
                                orderTransfer(transferOrderCheque, it)
                                        .mapError(TransferMoneyError::toText)
                            }
                            .mapSuccess { it.apply { publishEvents(this) } }
                            .mapSuccess { it.find { it is AccountDebited } as AccountDebited }
                            .flatMapSuccess { saveAccount(it.account) }
                            .unsafeRunSync()
                }

    }
}