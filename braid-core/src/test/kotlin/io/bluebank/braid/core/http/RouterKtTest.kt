/**
 * Copyright 2018 Royal Bank of Scotland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.bluebank.braid.core.http

import io.bluebank.braid.core.async.assertFails
import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.onSuccess
import io.bluebank.braid.core.async.withFuture
import io.bluebank.braid.core.json.BraidJacksonInit
import io.bluebank.braid.core.logging.LogInitialiser
import io.bluebank.braid.core.socket.findFreePort
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.Router
import io.vertx.kotlin.core.json.jsonArrayOf
import io.vertx.kotlin.core.json.jsonObjectOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(VertxUnitRunner::class)
class RouterKtTest {

  companion object {
    init {
      BraidJacksonInit.init()
      LogInitialiser.init()
    }
  }

  private val vertx = Vertx.vertx()
  private val port = findFreePort()
  private val client = vertx.createHttpClient(
    HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port)
  )

  private val httpServer = vertx.createHttpServer()
    .requestHandler(Router.router(vertx).apply {
      get("/string").handler { it.end("string") }
      get("/object").handler { it.end(Person("fred")) }
      get("/jsonobject").handler { it.end(jsonObjectOf("name" to "value")) }
      get("/jsonarray").handler { it.end(jsonArrayOf(1, 2, 3)) }
      get("/error").handler { it.end(RuntimeException("error")) }
    })

  @Before
  fun before(context: TestContext) {
    val async = context.async()
    httpServer
      .listen(port) {
        when {
          it.succeeded() -> async.complete()
          else -> context.fail(it.cause())
        }
      }
  }

  @After
  fun after(context: TestContext) {
    val async = context.async()
    client.close()
    vertx.close { async.complete() }
  }

  @Test
  fun `that we can end a routing context with a variety of types`(context: TestContext) {
    val async = context.async()
    client.getFuture("/string")
      .compose { it.body<String>() }
      .onSuccess { context.assertEquals("string", it, "that string response is correct") }
      .compose { client.getFuture("/object") }
      .compose { it.body<Person>() }
      .onSuccess {
        context.assertEquals(
          Person("fred"),
          it,
          "that object response matches"
        )
      }
      .compose { client.getFuture("/jsonobject") }
      .compose { it.body<JsonObject>() }
      .onSuccess {
        context.assertEquals(
          jsonObjectOf("name" to "value"),
          it,
          "that json object matches"
        )
      }

      .compose { client.getFuture("/jsonarray") }
      .compose { it.body<JsonArray>() }
      .onSuccess {
        context.assertEquals(
          jsonArrayOf(1, 2, 3),
          it,
          "that json array matches"
        )
      }
      .compose {
        client.getFuture("/error").recover { error ->
          context.assertEquals("error", error.message, "that error matches")
          Future.succeededFuture()
        }
      }
      .compose { withFuture<Void> { future -> httpServer.close(future) } }
      .compose { client.getFuture("/string").assertFails() }
      .onSuccess { async.complete() }
      .catch { context.fail(it) }
  }
}

data class Person(val name: String)
