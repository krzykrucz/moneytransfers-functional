package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Option
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
data class RejectedTransferOrderCheque(
    val orderer: Orderer,
    val title: Option<NonEmptyText>
)


data class AccountNumber private constructor(val number: NonEmptyText) {
    companion object {
        fun create(number: NonEmptyText): AccountNumber? =
            number.takeIf { "^[0-9]{26}$".toRegex().matches(number.text) }
                ?.let(::AccountNumber)
    }
}

//------
data class Customer(val name: NonEmptyText, val accountNumber: AccountNumber)

inline class RefNumber(val number: UUID)

sealed class TransferOrder {
    class InterBankTransferOrder : TransferOrder()
    data class IntraBankTransferOrder(val ref: RefNumber, val beneficiary: Customer) : TransferOrder()
}

//------

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

enum class DebitAccountFailure {
    InsufficientFunds
}

// result events
data class AccountDebitedEvent(val account: OrdererAccount)

