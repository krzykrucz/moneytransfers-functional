package com.krzykrucz.moneytransfers.domain.transfers

import kotlinx.coroutines.runBlocking


val fetchBankAccountFromDB: (Transaction) -> String = { tx ->
    tx.select()
        .also(::println)
        .also { error("I/O failed") }
}

fun main() {
    runBlocking {
        Exception::toString.onError {
            3.times.retry {
                inTransaction { tx ->
                    fetchBankAccountFromDB(tx)
                }
            }
        }
            .let(::println)

        val fetchAccountLikeABoss =
            fetchBankAccountFromDB + ::inTransaction + 3.times::retry + Exception::toString::onError

        println(fetchAccountLikeABoss())
    }
}


fun <R> ((Exception) -> R).onError(action: () -> R): R =
    try {
        action()
    } catch (e: Exception) {
        this(e)
    }

data class RetryCount(val count: Int)

val Int.times get() = RetryCount(this)

operator fun RetryCount.minus(int: Int) = copy(count = count - int)

fun <R> RetryCount.retry(action: () -> R): R =
    when (this) {
        0.times -> action()
        else -> try {
            action()
        } catch (error: Exception) {
            (this@retry - 1).retry(action)
        }
    }

class Transaction {
    fun select() = "transactional data"
}

fun <R> inTransaction(action: (Transaction) -> R): R =
    action(Transaction())
