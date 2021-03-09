@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Either
import arrow.core.Option
import arrow.core.flatMap
import arrow.core.toT
import com.krzykrucz.moneytransfers.domain.transfers.TransferEvent.IntraBankTransferOrdered
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

//data class AccountNumber(val number: NonEmptyText)
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


data class TransferChequeRejected(val reason: RawText)

//typealias ValidateTransfer = (TransferOrderCheque) -> TransferOrder

//typealias ValidateTransfer = (TransferOrderCheque) -> Either<TransferOrderRejected, TransferOrder>
//
typealias CheckTransferLimit = (TransferAmount) -> Boolean

typealias ValidateTransferCheque =
        (CheckTransferLimit, TransferOrderCheque) -> Either<TransferChequeRejected, ApprovedTransferOrderCheque>

//------
data class Customer(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class RefNumber(val number: UUID)

sealed class TransferOrder {
    class InterBankTransferOrder : TransferOrder()
    data class IntraBankTransferOrder(val ref: RefNumber, val beneficiary: Customer) : TransferOrder()
}

inline class SWIFT(val code: NonEmptyText)

sealed class FindBankError {
    object AccountNotFound : FindBankError()
    object ExternalServiceFailure : FindBankError()
}

typealias FindOutBank = suspend (AccountNumber) -> Either<FindBankError, SWIFT> // TODO change to IsSameBank=(AccNum)-> YesOrNo
//typealias ClassifyTransfer = (FindOutBank, TransferOrder) -> Transfer

data class FailureMessage(val text: RawText)

typealias ClassifyTransfer = (FindOutBank, ApprovedTransferOrderCheque) -> Either<FailureMessage, TransferOrder>

//typealias FindOutBank = (AccountNumber) -> Either<FindBankError, SWIFT>
//------

typealias DebitAccount = (OrdererAccount, TransferOrder) -> DebitedOrdererAccount


data class Money(
        val amount: BigDecimal,// TODO change to decimal
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


sealed class TransferMoneyError {
    object CannotClassifyTransfer : TransferMoneyError()
    object OrderInvalid : TransferMoneyError()
}


//whole workflow

data class IntraBankTransferOrderedEvent(val intraBankTransferOrder: TransferOrder.IntraBankTransferOrder)
data class AccountDebitedEvent(val account: OrdererAccount)

sealed class TransferEvent {
    data class IntraBankTransferOrdered(val event: IntraBankTransferOrderedEvent) : TransferEvent()
    data class AccountDebited(val event: AccountDebitedEvent) : TransferEvent()
}

typealias TransferEvents = List<TransferEvent>

//typealias OrderTransfer = (TransferOrderCheque, OrdererAccount) -> TransferEvents

//// error handling
//typealias OrderTransfer = (TransferOrderCheque, OrdererAccount) -> Either<TransferMoneyError, TransferEvents>
//
//// future
typealias OrderTransfer =
        suspend (TransferOrderCheque, OrdererAccount) -> Either<TransferMoneyError, TransferEvents>


//fun orderTransfer(
//        validateTransfer: ValidateTransfer,
//        classifyTransfer: ClassifyTransfer,
//        debitAccount: DebitAccount,
//        checkDailyLimit: CheckDailyLimit,
//        findOutBank: FindOutBank
//): OrderTransfer = { transferOrderCheque, ordererAccount ->
//    val transferOrder = validateTransfer(checkDailyLimit, transferOrderCheque)
//    val transfer: Transfer = classifyTransfer(findOutBank, transferOrder)
//    val debitedAccount = debitAccount(ordererAccount)
//    transferEvents(transfer, debitedAccount)
//}

fun orderTransfer(
        validateTransferCheque: ValidateTransferCheque,
        classifyTransfer: ClassifyTransfer,
        debitAccount: DebitAccount,
        checkTransferLimit: CheckTransferLimit,
        findOutBank: FindOutBank,
        createEvents: CreateEvents
): OrderTransfer = { transferOrderCheque, ordererAccount ->
    validateTransferCheque(checkTransferLimit, transferOrderCheque)
            .mapLeft { TransferMoneyError.OrderInvalid }
            .flatMap { order ->
                classifyTransfer(findOutBank, order)
                        .mapLeft { TransferMoneyError.CannotClassifyTransfer }
            }
            .map { transfer -> debitAccount(ordererAccount, transfer).toT(transfer) }
            .map { (account, transfer) -> createEvents(transfer, account) }
}


fun TransferOrder.toEvent(): TransferEvent? =
        when (this) {
            is TransferOrder.IntraBankTransferOrder ->
                IntraBankTransferOrderedEvent(this)
                        .let(::IntraBankTransferOrdered)
            else -> null
        }

fun OrdererAccount.toEvent(): TransferEvent =
        TransferEvent.AccountDebited(AccountDebitedEvent(this))

typealias CreateEvents = (TransferOrder, DebitedOrdererAccount) -> List<TransferEvent>

val createEvents: CreateEvents = { transfer, debitedOrdererAccount ->
    listOfNotNull(transfer.toEvent(), debitedOrdererAccount.toEvent())
}
