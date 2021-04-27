@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "NAME_SHADOWING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.*


typealias ValidateTransferCheque = (TransferMoneyCheque) -> ApprovedTransferOrderCheque
typealias ClassifyTransfer = (ApprovedTransferOrderCheque) -> TransferOrder
typealias DebitAccount = (SenderAccount, TransferOrder) -> DebitedOrdererAccount
typealias CreateEvent = (DebitedOrdererAccount) -> AccountDebitedEvent
//main workflow
typealias OrderTransfer =
    (ValidateTransferCheque, ClassifyTransfer, DebitAccount, CreateEvent) ->
    (TransferMoneyCheque, SenderAccount) -> AccountDebitedEvent

sealed class OrderTransferError {
    data class DebitFailed(val failure: DebitAccountFailure) : OrderTransferError()
    object ChequeValidationFailed : OrderTransferError()
}
