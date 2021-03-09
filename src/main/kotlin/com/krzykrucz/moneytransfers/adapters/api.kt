package com.krzykrucz.moneytransfers.adapters

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.krzykrucz.moneytransfers.domain.transfers.*
import com.virtuslab.basetypes.refined.NonEmptyText
import com.virtuslab.basetypes.refined.RawText


val transferOrderCheque = TransferOrderCheque(
        beneficiaryName = RawText("John Smith"),
        beneficiaryAccountNumber = RawText("10000000000000000000000000"),
        currency = RawText("USD"),
        amount = RawText("10"),
        ordererName = RawText("John Newman"),
        ordererAccountNumber = RawText("20000000000000000000000000"),
        title = RawText("Some kind of a transfer")
)

fun TransferMoneyError.toText(): String = this.javaClass.simpleName

typealias Request = String
typealias Response = String

val handleTransfer: (LoadAccount, SaveAccount, OrderTransfer, PublishEvents) -> suspend (Request) -> Either<Error, Response> =
        { loadAccount, saveAccount, orderTransfer, publishEvents ->
            { request ->

                // TODO deserialize request

                val accountNumberOrError = (NonEmptyText.of(transferOrderCheque.ordererAccountNumber.text)
                        ?.let((AccountNumber)::create)
                        ?.right()
                        ?: "Cannot deserialize".left())
                accountNumberOrError
                        .flatMap { accountNumber -> loadAccount(accountNumber) }
                        .flatMap { account ->
                            orderTransfer(transferOrderCheque, account)
                                    .mapLeft(TransferMoneyError::toText)
                        }
                        .map { events -> events.also { publishEvents(it) } }
                        .flatMap { saveAccount.ifDebited(it) }
                        .map { "Success" }
            }
        }

private suspend fun SaveAccount.ifDebited(events: TransferEvents): Either<Error, Unit> =
        (events.find { it is TransferEvent.AccountDebited } as? TransferEvent.AccountDebited)
                ?.let {
                    this.invoke(it.event.account)
                }
                ?: Unit.right()
