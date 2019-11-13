@file:Suppress("EXPERIMENTAL_FEATURE_WARNING", "DataClassPrivateConstructor")

package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Either
import arrow.core.Option
import arrow.core.maybe
import arrow.core.toT
import arrow.syntax.function.pipe
import com.krzykrucz.moneytransfers.domain.transfers.TransferOrder.IntraBankTransferOrder
import org.joda.money.Money
import java.util.*
import arrow.core.Option.Companion as Option1
import com.krzykrucz.moneytransfers.domain.transfers.TransferEvent.AccountDebited as AccountDebited
import com.krzykrucz.moneytransfers.domain.transfers.TransferEvent.TransferOrdered.IntraBankTransferOrdered as IntraBankTransferOrdered

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

typealias CheckTransferLimit = (TransferAmount) -> Boolean

typealias ValidateTransferCheque = (CheckTransferLimit, TransferOrderCheque) -> Either<TransferChequeRejected, ApprovedTransferOrderCheque>

//------
data class Customer(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class RefNumber(val number: UUID)

sealed class TransferOrder {
    class InterBankTransferOrder : TransferOrder()
    data class IntraBankTransferOrder(val ref: RefNumber, val beneficiary: Customer) : TransferOrder()
}

sealed class FindBankError {
    object AccountNotFound : FindBankError()
    object ExternalServiceFailure : FindBankError()
}

data class AccountClassificationFailure(val reason: Text)

typealias IsSameBank = (AccountNumber) -> AsyncOutput<Text, YesOrNo>

typealias ClassifyTransfer = (IsSameBank, ApprovedTransferOrderCheque) -> AsyncOutput<AccountClassificationFailure, TransferOrder>

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


//whole pipeline

sealed class TransferMoneyError {
    object CannotClassifyTransfer : TransferMoneyError()
    object ChequeInvalid : TransferMoneyError()
}

sealed class TransferEvent {
    sealed class TransferOrdered(val transferOrder: TransferOrder) : TransferEvent() {
        data class IntraBankTransferOrdered(val intraBankTransferOrder: IntraBankTransferOrder) : TransferOrdered(intraBankTransferOrder)
    }

    data class AccountDebited(val account: OrdererAccount) : TransferEvent()
}

typealias TransferEvents = List<TransferEvent>

typealias OrderTransfer = (TransferOrderCheque, OrdererAccount) -> AsyncOutput<TransferMoneyError, TransferEvents>


fun orderTransfer(
        validateTransferCheque: ValidateTransferCheque,
        classifyTransfer: ClassifyTransfer,
        debitAccount: DebitAccount,
        checkTransferLimit: CheckTransferLimit,
        isSameBank: IsSameBank,
        createEvents: CreateEvents
): OrderTransfer = { transferOrderCheque, ordererAccount ->
    validateTransferCheque(checkTransferLimit, transferOrderCheque)
            .pipe { AsyncOutput.just(it) }
            .mapError { TransferMoneyError.ChequeInvalid }
            .flatMapSuccess { order ->
                classifyTransfer(isSameBank, order)
                        .mapError { TransferMoneyError.CannotClassifyTransfer }
            }
            .mapSuccess { transfer -> debitAccount(ordererAccount, transfer).toT(transfer) }
            .mapSuccess { (account, transfer) -> createEvents(transfer, account) }
}

typealias CreateEvents = (TransferOrder, DebitedOrdererAccount) -> List<TransferEvent>

fun TransferOrder.toEvent(): Option<TransferEvent> =
        when (this) {
            is IntraBankTransferOrder ->
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