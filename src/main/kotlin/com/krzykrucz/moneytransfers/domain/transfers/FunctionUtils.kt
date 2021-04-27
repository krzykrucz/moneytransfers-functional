package com.krzykrucz.moneytransfers.domain.transfers

import arrow.core.andThen


operator fun <A, B> A.times(f: (A) -> B): B = this.let(f)

operator fun <A, B, C> ((A) -> B).times(g: (B) -> C): (A) -> C = this andThen g

operator fun <R> (() -> R).plus(decorator: (() -> R) -> R): () -> R = {
    decorator(this)
}

operator fun <P1, R> ((P1) -> R).plus(decorator: ((P1) -> R) -> R): () -> R = {
    decorator(this)
}
