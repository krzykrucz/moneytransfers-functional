package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.Either
import arrow.core.Option
import arrow.core.Predicate
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.maybe
import arrow.effects.IO
import org.joda.money.CurrencyUnit

typealias Text = String
typealias CurrencyCode = CurrencyUnit

data class NonEmptyText(val text: String) {
    companion object {
        fun of(string: String): Option<NonEmptyText> =
                string.isNotEmpty().maybe { NonEmptyText(string) }
    }
}

data class WholeNumber private constructor(val number: Int) {
    operator fun minus(number: WholeNumber): WholeNumber? = of(this.number - number.number)

    companion object {
        val ZERO = WholeNumber(0)
        val ONE = WholeNumber(1)
        val TWO = WholeNumber(2)
        val THREE = WholeNumber(3)
        fun of(int: Int): WholeNumber? =
                if (int >= 0) WholeNumber(int)
                else null
    }
}

data class NaturalNumber private constructor(val number: Int) {
    companion object {
        val ONE = NaturalNumber(1)
        val TWO = NaturalNumber(2)
        val THREE = NaturalNumber(3)
        fun of(int: Int): NaturalNumber? =
                if (int > 0) NaturalNumber(int)
                else null
    }
}

data class NonNegativeRealNumber private constructor(val number: Double) {
    operator fun minus(number: NonNegativeRealNumber): NonNegativeRealNumber? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        val ZERO: NonNegativeRealNumber = NonNegativeRealNumber(0.0)

        fun of(double: Double): NonNegativeRealNumber? =
                if (double >= 0) NonNegativeRealNumber(double)
                else null
    }
}

class NonEmptySet<T> private constructor(private val elements: Set<T>) : Set<T> by elements {
    companion object {
        fun <T> create(element: T): NonEmptySet<T> = NonEmptySet(setOf(element))

        fun <T> create(first: T, vararg rest: T): NonEmptySet<T> = NonEmptySet(setOf(first) + rest)

        fun <T> create(set: Set<T>): NonEmptySet<T>? = NonEmptySet(set)
    }

}

typealias Output<Error, Success> = Either<Error, Success>
typealias AsyncOutput<Error, Success> = IO<Output<Error, Success>>


fun <Success, Error> AsyncOutput<Error, Success>.failIf(predicate: Predicate<Success>, error: Error): AsyncOutput<Error, Success> {
    return this.map { either -> either.flatMap { success: Success -> if (predicate(success)) Either.Left(error) else Either.Right(success) } }
}


fun <S1, Error, S2> AsyncOutput<Error, S1>.mapSuccess(transformer: (S1) -> S2): AsyncOutput<Error, S2> {
    return this.map { either -> either.map(transformer) }
}

fun <Success, E1, E2> AsyncOutput<E1, Success>.mapError(transformer: (E1) -> E2): AsyncOutput<E2, Success> {
    return this.map { either -> either.mapLeft(transformer) }
}

fun <S1, Error, S2> AsyncOutput<Error, S1>.flatMapSuccess(transformer: (S1) -> AsyncOutput<Error, S2>): AsyncOutput<Error, S2> {
    return this.flatMap { either ->
        either.map { transformer(it) }
                .getOrHandle { IO.just(Either.left(it)) }
    }
}

fun <E, T> Output<E, T>.isSuccess() = this.isRight()
fun <E, T> Output<E, T>.isError() = this.isLeft()

class AsyncFactory {
    companion object {
        fun <S> justSuccess(success: S) = IO.just(Either.right(success))
        fun <E> justError(error: E) = IO.just(Either.left(error))
    }
}
