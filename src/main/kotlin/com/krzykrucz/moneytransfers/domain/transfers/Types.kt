@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Either
import arrow.core.Option
import arrow.core.maybe
import arrow.core.toT
import arrow.syntax.function.pipe
import org.joda.money.Money
import java.util.*
import arrow.core.Option.Companion as Option1

// cheque:
// beneficiary name, beneficiary acc number, currency, amount, orderer account number, orderer name, title

data class TransferOrderCheque(
        val beneficiaryName: Text,
        val beneficiaryAccountNumber: Text,
        val currency: Text,
        val amount: Text,
        val ordererName: Text,
        val ordererAccountNumber: Text,
        val title: Text
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


data class TransferChequeRejected(val reason: Text)

//typealias ValidateTransfer = (TransferOrderCheque) -> TransferOrder

//typealias ValidateTransfer = (TransferOrderCheque) -> Either<TransferOrderRejected, TransferOrder>
//
typealias CheckTransferLimit = (TransferAmount) -> Boolean

typealias ValidateTransferCheque = (CheckTransferLimit, TransferOrderCheque) -> Either<TransferChequeRejected, ApprovedTransferOrderCheque>

//------
data class Customer(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class RefNumber(val number: UUID)

sealed class TransferOrder {
    class InterBankTransferOrder : TransferOrder()
    data class IntraBankTransferOrder(val ref: RefNumber, val beneficiary: Customer) : TransferOrder()
}

inline class SWIFT(val code: NonEmptyText)

sealed class FindBankError {
    class AccountNotFound
    class ExternalServiceFailure
}

typealias FindOutBank = (AccountNumber) -> AsyncOutput<FindBankError, SWIFT>
//typealias ClassifyTransfer = (FindOutBank, TransferOrder) -> Transfer

typealias ClassifyTransfer = (FindOutBank, ApprovedTransferOrderCheque) -> AsyncOutput<Text, TransferOrder>

//typealias FindOutBank = (AccountNumber) -> Either<FindBankError, SWIFT>
//------

typealias DebitAccount = (OrdererAccount, TransferOrder) -> DebitedOrdererAccount

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


sealed class TransferMoneyError {
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
        (TransferOrderCheque, OrdererAccount) -> AsyncOutput<TransferMoneyError, TransferEvents>


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
            .pipe { AsyncOutput.just(it) }
            .mapError { TransferMoneyError.OrderInvalid }
            .flatMapSuccess { order ->
                classifyTransfer(findOutBank, order)
                        .mapError { TransferMoneyError.CannotClassifyTransfer }
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