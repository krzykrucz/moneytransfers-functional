@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "NAME_SHADOWING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.*


typealias ValidateTransferCheque = (TransferOrderCheque) -> Either<RejectedTransferOrderCheque, ApprovedTransferOrderCheque>
typealias ClassifyTransfer = (ApprovedTransferOrderCheque) -> TransferOrder
typealias DebitAccount = (OrdererAccount, TransferOrder) -> Either<DebitAccountFailure, DebitedOrdererAccount>
typealias CreateEvent = (DebitedOrdererAccount) -> AccountDebitedEvent
//main workflow
typealias OrderTransfer =
    (ValidateTransferCheque, ClassifyTransfer, DebitAccount, CreateEvent) ->
    (TransferOrderCheque, OrdererAccount) -> Either<OrderTransferError, AccountDebitedEvent>

sealed class OrderTransferError {
    data class DebittingFailed(val failure: DebitAccountFailure) : OrderTransferError()
    object ChequeValidationFailed : OrderTransferError()
}

val orderTransfer: OrderTransfer =
    { validateTransferCheque, classifyTransfer, debitAccount, createEvent ->
        val validateTransferCheque = validateTransferCheque.adapt { OrderTransferError.ChequeValidationFailed }
        val classifyTransfer = classifyTransfer.adapt()
        val createEvent = createEvent.adapt()
        ;
        { transferOrderCheque, ordererAccount ->
            val debitAccount = debitAccount.partially1(ordererAccount).adapt(OrderTransferError::DebittingFailed)

            transferOrderCheque.adapt() *
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
