package com.krzykrucz.moneytransfers.adapters

import arrow.core.Option
import com.krzykrucz.moneytransfers.domain.transfers.*

val transferOrderCheque = TransferOrderCheque(
        "John Smith", "10000000000000000000000000",
        "USD", "10",
        "John Newman", "20000000000000000000000000",
        "Some kind of a transfer"
)


class TransferHandler(val loadAccount: LoadAccount,
                      val saveAccount: SaveAccount,
                      val orderTransfer: OrderTransfer,
                      val publishEvents: PublishEvents) {

    fun handle(requestBody: String) {
//        TODO("deserialize")

        Option.fromNullable(NonEmptyText.of(transferOrderCheque.ordererAccountNumber))
                .flatMap { text -> AccountNumber.create(text) }
                .map {
                    loadAccount(it)
                            .flatMapSuccess{orderTransfer(transferOrderCheque, it)}
                            .mapSuccess { it.find { it is AccountDebited } as AccountDebited }
                            .mapSuccess { saveAccount(it.account) }
                            .unsafeRunSync()
                }


    }
}