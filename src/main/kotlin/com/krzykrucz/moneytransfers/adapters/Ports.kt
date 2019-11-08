package com.krzykrucz.moneytransfers.adapters

import com.krzykrucz.moneytransfers.domain.transfers.AccountNumber
import com.krzykrucz.moneytransfers.domain.transfers.AsyncOutput
import com.krzykrucz.moneytransfers.domain.transfers.OrdererAccount
import com.krzykrucz.moneytransfers.domain.transfers.TransferEvents

typealias LoadAccount = (AccountNumber) -> AsyncOutput<String, OrdererAccount>
typealias SaveAccount = (OrdererAccount) -> AsyncOutput<String, Unit>
typealias PublishEvents = (TransferEvents) -> AsyncOutput<String, Unit>