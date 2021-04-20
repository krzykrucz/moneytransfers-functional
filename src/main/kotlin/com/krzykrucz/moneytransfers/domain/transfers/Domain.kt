@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "NAME_SHADOWING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Either
import arrow.core.andThen
import arrow.core.flatMap
import arrow.core.right
import arrow.syntax.function.partially1
import arrow.syntax.function.pipe
import com.krzykrucz.moneytransfers.domain.transfers.OrderTransferError.ChequeValidationFailed
import com.krzykrucz.moneytransfers.domain.transfers.OrderTransferError.DebittingFailed


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
        val validateTransferCheque = validateTransferCheque.adapt { ChequeValidationFailed }
        val classifyTransfer = classifyTransfer.adapt()
        val createEvent = createEvent.adapt()
        ;
        { transferOrderCheque, ordererAccount ->
            val debitAccount = debitAccount.partially1(ordererAccount).adapt(::DebittingFailed)

            transferOrderCheque.adapt() *
                validateTransferCheque *
                classifyTransfer *
                debitAccount *
                createEvent
        }
    }

operator fun <A, B> A.times(f: (A) -> B): B = this pipe f
operator fun <A, B, C> ((A) -> B).times(g: (B) -> C): (A) -> C = this andThen g

typealias Rail<E, A, B> = (Either<E, A>) -> Either<E, B>
typealias DomainRail<A, B> = Rail<OrderTransferError, A, B>

fun <A, B> ((A) -> B).adapt(): DomainRail<A, B> =
    { either ->
        either.map { p1 ->
            this(p1)
        }
    }

fun <A, E, B> ((A) -> Either<E, B>).adapt(errorMapper: (E) -> OrderTransferError): DomainRail<A, B> =
    { either ->
        either.flatMap { p1 ->
            this(p1).mapLeft(errorMapper)
        }
    }

fun <A> A.adapt(): Either<OrderTransferError, A> =
    this.right()
