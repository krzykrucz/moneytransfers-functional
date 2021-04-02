@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "NAME_SHADOWING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Option
import arrow.core.andThen
import arrow.syntax.function.partially1
import arrow.syntax.function.pipe
import com.virtuslab.basetypes.refined.NaturalNumber
import com.virtuslab.basetypes.refined.NonEmptyText
import com.virtuslab.basetypes.refined.NonNegativeRealNumber
import com.virtuslab.basetypes.refined.RawText
import java.math.BigDecimal
import java.util.*

// cheque:
// beneficiary name, beneficiary acc number, currency, amount, orderer account number, orderer name, title

data class TransferOrderCheque(
        val beneficiaryName: RawText,
        val beneficiaryAccountNumber: RawText,
        val currency: RawText,
        val amount: RawText,
        val ordererName: RawText,
        val ordererAccountNumber: RawText,
        val title: RawText
)

data class Orderer(val name: NonEmptyText, val accountNumber: AccountNumber)
data class Beneficiary(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class TransferAmount(val amount: NonNegativeRealNumber)

sealed class CurrencyCode {
    object EUR : CurrencyCode()
    object PLN : CurrencyCode()
    object USD : CurrencyCode()
}

data class ApprovedTransferOrderCheque(
        val beneficiary: Beneficiary,
        val orderer: Orderer,
        val amount: TransferAmount,
        val currency: CurrencyCode,
        val title: Option<NonEmptyText>
)


data class AccountNumber private constructor(val number: NonEmptyText) {
    companion object {
        fun create(number: NonEmptyText): AccountNumber? =
                number.takeIf { "^[0-9]{26}$".toRegex().matches(number.text) }
                        ?.let(::AccountNumber)
    }
}

// wofklows
typealias CheckTransferLimit = (TransferAmount) -> Boolean

typealias ValidateTransferCheque =
        (CheckTransferLimit, TransferOrderCheque) -> ApprovedTransferOrderCheque

//------
data class Customer(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class RefNumber(val number: UUID)

sealed class TransferOrder {
    class InterBankTransferOrder : TransferOrder()
    data class IntraBankTransferOrder(val ref: RefNumber, val beneficiary: Customer) : TransferOrder()
}

inline class SWIFT(val code: NonEmptyText)

typealias FindOutBank = suspend (AccountNumber) -> SWIFT

typealias ClassifyTransfer = (FindOutBank, ApprovedTransferOrderCheque) -> TransferOrder

//------

typealias DebitAccount = (OrdererAccount, TransferOrder) -> DebitedOrdererAccount


data class Money(
        val amount: BigDecimal,
        val currency: CurrencyCode
)

inline class AccountBalance(val balance: Money)

inline class DailyLimit(val limit: NaturalNumber)

sealed class TransferLimit {
    data class Daily(val limit: DailyLimit) : TransferLimit()
    object None : TransferLimit()
}

data class OrdererAccount(
        val accountNumber: AccountNumber,
        val balance: AccountBalance,
        val transferLimit: TransferLimit
)
typealias DebitedOrdererAccount = OrdererAccount

// result events
data class AccountDebitedEvent(val account: OrdererAccount)

typealias CreateEvent = (DebitedOrdererAccount) -> AccountDebitedEvent

//whole workflow
typealias OrderTransfer =
        (CheckTransferLimit, FindOutBank, ValidateTransferCheque, ClassifyTransfer, DebitAccount, CreateEvent) ->
        suspend (TransferOrderCheque, OrdererAccount) -> AccountDebitedEvent

operator fun <A, B, C> ((A) -> B).times(g: (B) -> C): (A) -> C = this andThen g
operator fun <A, B> A.times(f: (A) -> B): B = this pipe f
operator fun <P1, P2, R> ((P1, P2) -> R).plus(p1: P1): (P2) -> R = partially1(p1)

val orderTransfer: OrderTransfer =
        { checkTransferLimit, findOutBank, validateTransferCheque, classifyTransfer, debitAccount, createEvents ->
            val validateTransferCheque = validateTransferCheque.partially1(checkTransferLimit)
            val classifyTransfer = classifyTransfer.partially1(findOutBank);
            { transferOrderCheque, ordererAccount ->
                val debitAccount = debitAccount.partially1(ordererAccount)

                transferOrderCheque *
                        validateTransferCheque *
                        classifyTransfer *
                        debitAccount *
                        createEvents
            }
        }


