package com.krzykrucz.moneytransfers.domain.transfers

typealias LoadAccount = (AccountNumber) -> AsyncOutput<Exception, OrdererAccount>
typealias SaveAccount = (OrdererAccount) -> AsyncOutput<Exception, Unit>
typealias PublishEvents = (TransferEvents) -> AsyncOutput<Exception, Unit>