package com.krzykrucz.moneytransfers.adapters

import com.krzykrucz.moneytransfers.domain.transfers.AsyncOutput
import com.krzykrucz.moneytransfers.domain.transfers.OrdererAccount
import com.krzykrucz.moneytransfers.domain.transfers.SaveAccount

class StoreAccountInMemory: SaveAccount {
    override fun invoke(p1: OrdererAccount): AsyncOutput<Exception, Unit> {
        TODO()
    }

}


