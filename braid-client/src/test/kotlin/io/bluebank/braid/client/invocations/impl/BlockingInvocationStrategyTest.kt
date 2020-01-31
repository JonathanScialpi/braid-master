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

import io.bluebank.braid.core.jsonrpc.JsonRPCResultResponse
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Future
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertFailsWith

class BlockingInvocationStrategyTest {
  companion object {
    private val log = loggerFor<BlockingInvocationStrategyTest>()
  }

  @Test
  fun `that receiving an onComplete message on a blocking strategy that has completed is not the end of the world`() {
    var requestId: Long = -1

    val invocations = MockInvocations {
      requestId = it.id
      log.trace("invoking", it)
      receive(JsonRPCResultResponse(id = requestId, result = "hello"))
      Future.succeededFuture()
    }

    val strategy = BlockingInvocationStrategy(
      invocations,
      TestInterface::testBlocking.name,
      TestInterface::testBlocking.javaMethod?.genericReturnType!!,
      arrayOf()
    )

    var result: String? = null
    val latch = CountDownLatch(1)
    Thread {
      result = strategy.getResult() as String
      latch.countDown()
    }.start()

    latch.await()
    assertEquals("hello", result)

    strategy.onCompleted(requestId)
    assertFailsWith<IllegalStateException> {
      strategy.getResult()
    }

    assertFailsWith<IllegalStateException> {
      strategy.onNext(1, "world")
    }
  }

  @Test
  fun `that strategy that has not be setup cannot receive messages`() {
    val invocations = MockInvocations { Future.succeededFuture() }

    val strategy = BlockingInvocationStrategy(
      invocations,
      TestInterface::testBlocking.name,
      TestInterface::testBlocking.javaMethod?.genericReturnType!!,
      arrayOf()
    )
    assertFailsWith<IllegalStateException> {
      strategy.onNext(1, "hello")
    }
  }

  @Test
  fun `that receiving an invalid message throws`() {
    var requestId: Long = -1

    val invocations = MockInvocations {
      requestId = it.id
      log.trace("invoking", it)
      Future.succeededFuture()
    }

    val strategy = BlockingInvocationStrategy(
      invocations,
      TestInterface::testBlocking.name,
      TestInterface::testBlocking.javaMethod?.genericReturnType!!,
      arrayOf()
    )

    Thread {
      strategy.getResult() as String // triggers the invocation
    }.start()

    assertFailsWith<IllegalStateException> {
      strategy.onNext(requestId + 1, "hello") // wrong id
    }

    assertFailsWith<IllegalStateException> {
      strategy.onCompleted(requestId) // premature completion
    }
  }
}
