package com.krzykrucz.moneytransfers

import org.jooby.Jooby.*
import org.jooby.Kooby

/**
 * Gradle Kotlin stater project.
 */
class App : Kooby({
    get {
        val name = param("name").value("Kotlin")
        "Hello $name!"
    }
})

/**
 * Run application:
 */
fun main(args: Array<String>) {
    run(::App, args)
}
