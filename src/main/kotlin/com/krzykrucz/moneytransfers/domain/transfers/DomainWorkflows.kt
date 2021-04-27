@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "NAME_SHADOWING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.*


typealias ValidateTransferCheque = (TransferMoneyCheque) -> Either<RejectedTransferOrderCheque, ApprovedTransferOrderCheque>
typealias ClassifyTransfer = (ApprovedTransferOrderCheque) -> TransferOrder
typealias DebitAccount = (SenderAccount, TransferOrder) -> Either<DebitAccountFailure, DebitedOrdererAccount>
typealias CreateEvent = (DebitedOrdererAccount) -> AccountDebitedEvent
//main workflow
typealias OrderTransfer =
    (ValidateTransferCheque, ClassifyTransfer, DebitAccount, CreateEvent) ->
    (TransferMoneyCheque, SenderAccount) -> Either<OrderTransferError, AccountDebitedEvent>

sealed class OrderTransferError {
    data class DebitFailed(val failure: DebitAccountFailure) : OrderTransferError()
    object ChequeValidationFailed : OrderTransferError()
}

val orderTransfer: OrderTransfer =
    { validateTransferCheque, classifyTransfer, debitAccount, createEvent ->
        ;
        { cheque, senderAccount ->
            val debitAccount = debitAccount.partially1(senderAccount)

            cheque *
                validateTransferCheque *
                classifyTransfer *
                debitAccount *
                createEvent
        }
    }

typealias TwoTracks<E, A, B> = (Either<E, A>) -> Either<E, B>
typealias BankingTwoTracks<A, B> = TwoTracks<OrderTransferError, A, B>

fun <A, B> ((A) -> B).adapt(): BankingTwoTracks<A, B> =
    { either ->
        either.map { p1 ->
            this(p1)
        }
    }

fun <A, E, B> ((A) -> Either<E, B>).adapt(errorMapper: (E) -> OrderTransferError): BankingTwoTracks<A, B> =
    { either ->
        either.flatMap { p1 ->
            this(p1).mapLeft(errorMapper)
        }
    }

fun <A> A.adapt(): Either<OrderTransferError, A> =
    this.right()
