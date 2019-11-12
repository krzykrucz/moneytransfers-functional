package com.krzykrucz.moneytransfers

import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import org.amshove.kluent.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jooby.Status

object AppTest : Spek({
    jooby(App()) {
        describe("Get /") {
            given("queryParameter name=Victor") {
                it("should return Hello Victor!") {
                    val name = "Victor"
                    given()
                            .queryParam("name", name)
                            .`when`()
                            .get("/")
                            .then()
                            .assertThat()
                            .statusCode(Status.OK.value())
                            .extract()
                            .asString()
                            .let {
                                it shouldEqual "Hello $name!"
                            }
                }
            }

            given("no queryParameter") {
                it("should return Kotlin as the default name") {
                    get("/")
                            .then()
                            .assertThat()
                            .statusCode(Status.OK.value())
                            .extract()
                            .asString()
                            .let {
                                it shouldEqual "Hello Kotlin!"
                            }
                }
            }
        }
    }
})