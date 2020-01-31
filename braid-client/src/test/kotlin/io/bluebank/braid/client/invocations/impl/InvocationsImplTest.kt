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
package io.bluebank.braid.client.invocations.impl

import io.bluebank.braid.client.BraidClientConfig
import io.bluebank.braid.client.invocations.Invocations
import io.bluebank.braid.core.jsonrpc.JsonRPCResultResponse
import io.bluebank.braid.core.socket.findFreePort
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.Json
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ConnectException
import java.net.URI
import kotlin.test.assertFailsWith

@RunWith(VertxUnitRunner::class)
class InvocationsImplTest {

  private val port = findFreePort()
  private val vertx = Vertx.vertx()
  private var server: HttpServer? = null
  private var invocations: Invocations? = null

  @After
  fun after(context: TestContext) {
    val async = context.async()
    server?.close {
      invocations?.close()
      vertx.close {
        async.complete()
      }
    } ?: vertx.close {
      async.complete()
    }
  }

  @Test
  fun `that sending a malformed request does not break the flow`(context: TestContext) {
    val async = context.async()
    server = vertx.createHttpServer()
      .websocketHandler { socket ->
        socket.handler {
          socket.writeFinalTextFrame("&&&&&&&&&&")
          socket.writeFinalTextFrame(
            Json.encode(
              JsonRPCResultResponse(
                id = 1,
                result = "hello"
              )
            )
          )
        }
      }.listen(port) {
        async.complete()
      }
    async.await()
    invocations = Invocations.create(vertx = vertx,
      config = BraidClientConfig(URI("http://localhost:$port/api"), tls = false),
      exceptionHandler = { err ->
        context.fail(err)
      },
      closeHandler = {
      })
    val result = invocations?.invoke("foo", String::class.java, arrayOf()) as String
    context.assertEquals("hello", result)
  }

  @Test
  fun `that trying to connect to a non existent uri fails`(context: TestContext) {
    assertFailsWith<ConnectException> {
      Invocations.create(vertx = vertx,
        config = BraidClientConfig(URI("http://localhost:$port/api"), tls = false),
        exceptionHandler = {
          context.fail(it)
        },
        closeHandler = {
        })
    }
  }

  @Test(expected = IllegalStateException::class)
  fun `that submitting strategies with duplicate ids does not fail - should ra`() {
    val invocations =
      MockInvocations(invocationTarget = { parent, method, returnType, params ->
        parent.setStrategy(
          1,
          object : InvocationStrategy<Any>(parent, method, returnType, params) {
            override fun getResult(): Any {
              error("should not be called")
            }

            override fun onNext(requestId: Long, item: Any?) {
              error("should not be called")
            }

            override fun onError(requestId: Long, error: Throwable) {
              error("should not be called")
            }

            override fun onCompleted(requestId: Long) {
              error("should not be called")
            }
          })
      })
    invocations.invoke("foo", Any::class.java, emptyArray())
    invocations.invoke("foo", Any::class.java, emptyArray())
  }

  @Test()
  fun `that removing an unregistered strategy does not fail - should write to logs`() {
    val invocations = MockInvocations()
    invocations.removeStrategy(1)
  }

}