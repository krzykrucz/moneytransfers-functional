@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Option
import arrow.core.maybe
import arrow.core.toT
import arrow.syntax.function.pipe
import com.virtuslab.basetypes.refined.NaturalNumber
import com.virtuslab.basetypes.refined.NonEmptyText
import com.virtuslab.basetypes.refined.NonNegativeRealNumber
import com.virtuslab.basetypes.refined.RawText
import com.virtuslab.basetypes.result.Result
import com.virtuslab.basetypes.result.arrow.*
import com.virtuslab.basetypes.result.mapError
import java.math.BigDecimal
import java.util.*
import arrow.core.Option.Companion as Option1

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
        fun create(number: NonEmptyText): Option<AccountNumber> =
                "^[0-9]{26}$"
                        .toRegex()
                        .matches(number.text)
                        .maybe { AccountNumber(number) }
    }
}


data class TransferChequeRejected(val reason: RawText): Exception()

//typealias ValidateTransfer = (TransferOrderCheque) -> TransferOrder

//typealias ValidateTransfer = (TransferOrderCheque) -> Either<TransferOrderRejected, TransferOrder>
//
typealias CheckTransferLimit = (TransferAmount) -> Boolean

typealias ValidateTransferCheque = (CheckTransferLimit, TransferOrderCheque) -> Result<ApprovedTransferOrderCheque, TransferChequeRejected>

//------
data class Customer(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class RefNumber(val number: UUID)

sealed class TransferOrder {
    class InterBankTransferOrder : TransferOrder()
    data class IntraBankTransferOrder(val ref: RefNumber, val beneficiary: Customer) : TransferOrder()
}

inline class SWIFT(val code: NonEmptyText)

sealed class FindBankError : Exception() {
    class AccountNotFound
    class ExternalServiceFailure
}

typealias FindOutBank = (AccountNumber) -> AsyncResult<SWIFT, FindBankError>
//typealias ClassifyTransfer = (FindOutBank, TransferOrder) -> Transfer

data class FailureMessage(val text: RawText) : Exception()

typealias ClassifyTransfer = (FindOutBank, ApprovedTransferOrderCheque) -> AsyncResult<TransferOrder, FailureMessage>

//typealias FindOutBank = (AccountNumber) -> Either<FindBankError, SWIFT>
//------

typealias DebitAccount = (OrdererAccount, TransferOrder) -> DebitedOrdererAccount


data class Money(
        val amount: BigDecimal,
        val currency: CurrencyCode
)

inline class AccountBalance(val balance: Money)

sealed class TransferLimit {
    data class Daily(val limit: NaturalNumber) : TransferLimit()
    object None : TransferLimit()
}

data class OrdererAccount(
        val accountNumber: AccountNumber,
        val balance: AccountBalance,
        val transferLimit: TransferLimit
)
typealias DebitedOrdererAccount = OrdererAccount


sealed class TransferMoneyError : Exception() {
    object CannotClassifyTransfer : TransferMoneyError()
    object OrderInvalid : TransferMoneyError()
}


//whole workflow
sealed class TransferEvent

sealed class TransferOrdered(val transferOrder: TransferOrder) : TransferEvent()
data class IntraBankTransferOrdered(val intraBankTransferOrder: TransferOrder.IntraBankTransferOrder) : TransferOrdered(intraBankTransferOrder)
data class AccountDebited(val account: OrdererAccount) : TransferEvent()

typealias TransferEvents = List<TransferEvent>

//typealias OrderTransfer = (TransferOrderCheque, OrdererAccount) -> TransferEvents

//// error handling
//typealias OrderTransfer = (TransferOrderCheque, OrdererAccount) -> Either<TransferMoneyError, TransferEvents>
//
//// future
typealias OrderTransfer =
        (TransferOrderCheque, OrdererAccount) -> AsyncResult<TransferEvents, TransferMoneyError>


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
            .mapError { TransferMoneyError.OrderInvalid }
            .liftAsync()
            .flatMapSuccess { order ->
                classifyTransfer(findOutBank, order)
                        .mapFailure { TransferMoneyError.CannotClassifyTransfer }
            }
            .mapSuccess { transfer -> debitAccount(ordererAccount, transfer).toT(transfer) }
            .mapSuccess { (account, transfer) -> createEvents(transfer, account) }
}

typealias CreateEvents = (TransferOrder, DebitedOrdererAccount) -> List<TransferEvent>

fun TransferOrder.toEvent(): Option<TransferEvent> =
        when (this) {
            is TransferOrder.IntraBankTransferOrder ->
                IntraBankTransferOrdered(this)
                        .pipe(Option1::just)
            else -> Option1.empty()
        }

val createEvents: CreateEvents = { transfer, debitedOrdererAccount ->
    transfer.toEvent()
            .toList()
            .toMutableList()
            .plus(AccountDebited(debitedOrdererAccount))
}