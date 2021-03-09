package com.krzykrucz.moneytransfers.adapters

import arrow.core.Either
import com.krzykrucz.moneytransfers.domain.transfers.AccountNumber
import com.krzykrucz.moneytransfers.domain.transfers.OrdererAccount
import com.krzykrucz.moneytransfers.domain.transfers.TransferEvents

typealias Error = String

typealias LoadAccount = suspend (AccountNumber) -> Either<Error, OrdererAccount>
typealias SaveAccount = suspend (OrdererAccount) -> Either<Error, Unit>
typealias PublishEvents = suspend (TransferEvents) -> Either<Error, Unit>
