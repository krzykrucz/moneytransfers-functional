package com.krzykrucz.moneytransfers.domain.transfers

typealias LoadAccount = (AccountNumber) -> AsyncOutput<Exception, OrdererAccount>
typealias SaveAccount = (OrdererAccount) -> AsyncOutput<Exception, Unit>
typealias PublishEvents = (TransferOrdered) -> AsyncOutput<Exception, Unit>