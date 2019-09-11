[![Build Status](https://travis-ci.org/jooby-project/kotlin-gradle-starter.svg?branch=master)](https://travis-ci.org/jooby-project/kotlin-gradle-starter)
# kotlin-gradle-starter

Starter kit for [Kotlin](http://kotlinlang.org/), using [gradle](https://gradle.org/) and [spek](http://spekframework.org/) for testing

## quick preview

This project contains a simple `Hello World` application.

```kotlin
import org.jooby.Jooby.*
import org.jooby.Kooby

class App : Kooby({
    get {
        val name = param("name").value("Kotlin")
        "Hello $name!"
    }
})

fun main(args: Array<String>) {
    run(::App, args)
}
```

## run

    ./gradlew joobyRun

## test
    ./gradlew test

## help

* Read the [module documentation](http://jooby.org/doc/lang-kotlin)
* Join the [channel](https://gitter.im/jooby-project/jooby)
* Join the [group](https://groups.google.com/forum/#!forum/jooby-project)
