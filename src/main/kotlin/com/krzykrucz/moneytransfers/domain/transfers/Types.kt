@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Either
import arrow.core.Option
import arrow.core.Tuple2
import arrow.effects.IO
import org.joda.money.Money
import java.util.*

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
                if ("^[0-9]{26}$".toRegex().matches(number.text))
                    Option.just(AccountNumber(number))
                else
                    Option.empty()

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
inline class DailyLimit(val limit: NaturalNumber)
data class OrdererAccount(val balance: AccountBalance, val dailyLimit: DailyLimit)
typealias DebitedOrdererAccount = OrdererAccount


sealed class TransferMoneyError {
    class CannotClassifyTransfer : TransferMoneyError()
    class OrderInvalid : TransferMoneyError()
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
        findOutBank: FindOutBank
): OrderTransfer = { transferOrderCheque, ordererAccount ->
    AsyncFactory.justEither(validateTransfer(checkDailyLimit, transferOrderCheque))
            .mapError { TransferMoneyError.OrderInvalid() }
            .flatMapSuccess { classifyTransfer(findOutBank, it)
                    .mapError { TransferMoneyError.CannotClassifyTransfer() }}
            .mapSuccess { Tuple2(debitAccount(ordererAccount, it), it) }
            .mapSuccess { transferEvents(it.b, it.a) }
}

fun transferEvents(transfer: Transfer, debitedOrdererAccount: DebitedOrdererAccount) =
        when (transfer) {
            is Transfer.IntraBankTransfer -> Option.just(IntraBankTransferOrdered(transfer))
            else -> Option.empty()
        }.toList()
                .toMutableList()
                .plus(AccountDebited(debitedOrdererAccount))

