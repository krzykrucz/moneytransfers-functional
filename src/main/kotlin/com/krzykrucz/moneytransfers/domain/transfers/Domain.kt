@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "NAME_SHADOWING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.andThen
import arrow.syntax.function.partially1
import arrow.syntax.function.pipe


typealias ValidateTransferCheque = (TransferOrderCheque) -> ApprovedTransferOrderCheque
typealias ClassifyTransfer = (ApprovedTransferOrderCheque) -> TransferOrder
typealias DebitAccount = (OrdererAccount, TransferOrder) -> DebitedOrdererAccount
typealias CreateEvent = (DebitedOrdererAccount) -> AccountDebitedEvent
//main workflow
typealias OrderTransfer =
    (ValidateTransferCheque, ClassifyTransfer, DebitAccount, CreateEvent) ->
    (TransferOrderCheque, OrdererAccount) -> AccountDebitedEvent


val orderTransfer: OrderTransfer =
    { validateTransferCheque, classifyTransfer, debitAccount, createEvent ->
        { transferOrderCheque, ordererAccount ->
            val debitAccount = debitAccount.partially1(ordererAccount)

            transferOrderCheque *
                validateTransferCheque *
                classifyTransfer *
                debitAccount *
                createEvent
        }
    }

operator fun <A, B> A.times(f: (A) -> B): B = this pipe f
operator fun <A, B, C> ((A) -> B).times(g: (B) -> C): (A) -> C = this andThen g



