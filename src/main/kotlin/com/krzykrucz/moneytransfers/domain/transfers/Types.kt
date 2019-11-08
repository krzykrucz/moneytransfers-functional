@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Either
import arrow.core.Option
import arrow.core.maybe
import arrow.core.toT
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

data class TransferOrder(
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


data class TransferOrderRejected(val reason: Text)

//typealias ValidateTransfer = (TransferOrderCheque) -> TransferOrder

//typealias ValidateTransfer = (TransferOrderCheque) -> Either<TransferOrderRejected, TransferOrder>
//
typealias CheckDailyLimit = (TransferAmount) -> Boolean

typealias ValidateTransfer = (CheckDailyLimit, TransferOrderCheque) -> Either<TransferOrderRejected, TransferOrder>

//------
data class Customer(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class RefNumber(val number: UUID)

sealed class Transfer {
    class InterBankTransfer : Transfer()
    data class IntraBankTransfer(val ref: RefNumber, val beneficiary: Customer) : Transfer()
}

inline class SWIFT(val code: NonEmptyText)

sealed class FindBankError {
    class AccountNotFound
    class ExternalServiceFailure
}

typealias FindOutBank = (AccountNumber) -> AsyncOutput<FindBankError, SWIFT>
//typealias ClassifyTransfer = (FindOutBank, TransferOrder) -> Transfer

typealias ClassifyTransfer = (FindOutBank, TransferOrder) -> AsyncOutput<Text, Transfer>

//typealias FindOutBank = (AccountNumber) -> Either<FindBankError, SWIFT>
//------

typealias DebitAccount = (OrdererAccount, Transfer) -> DebitedOrdererAccount

inline class AccountBalance(val balance: Money)

sealed class TransferLimit {
    data class Daily(val limit: NaturalNumber): TransferLimit()
    object None: TransferLimit()
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

sealed class TransferOrdered(val transfer: Transfer) : TransferEvent()
data class IntraBankTransferOrdered(val intraBankTransfer: Transfer.IntraBankTransfer) : TransferOrdered(intraBankTransfer)
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
        validateTransfer: ValidateTransfer,
        classifyTransfer: ClassifyTransfer,
        debitAccount: DebitAccount,
        checkDailyLimit: CheckDailyLimit,
        findOutBank: FindOutBank,
        createEvents: CreateEvents
): OrderTransfer = { transferOrderCheque, ordererAccount ->
    validateTransfer(checkDailyLimit, transferOrderCheque)
            .let { AsyncOutput.just(it) }
            .mapError { TransferMoneyError.OrderInvalid }
            .flatMapSuccess { order ->
                classifyTransfer(findOutBank, order)
                        .mapError { TransferMoneyError.CannotClassifyTransfer }
            }
            .mapSuccess { transfer -> debitAccount(ordererAccount, transfer).toT(transfer) }
            .mapSuccess { (account, transfer) -> createEvents(transfer, account) }
}

typealias CreateEvents = (Transfer, DebitedOrdererAccount) -> List<TransferEvent>

fun Transfer.toEvent(): Option<TransferEvent> =
        when (this) {
            is Transfer.IntraBankTransfer ->
                IntraBankTransferOrdered(this)
                        .let(Option1::just)
            else -> Option1.empty()
        }

val createEvents: CreateEvents = { transfer, debitedOrdererAccount ->
    transfer.toEvent()
            .toList()
            .toMutableList()
            .plus(AccountDebited(debitedOrdererAccount))
}