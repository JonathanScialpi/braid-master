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
package io.bluebank.braid.core.jsonrpc

import io.bluebank.braid.core.async.getOrThrow
import io.bluebank.braid.core.async.toFuture
import io.bluebank.braid.core.jsonrpc.JsonRPCError.Companion.METHOD_NOT_FOUND
import io.bluebank.braid.core.logging.loggerFor
import io.bluebank.braid.core.service.ConcreteServiceExecutor
import io.bluebank.braid.core.socket.NonBlockingSocket
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import rx.Observable
import rx.Subscriber
import rx.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(VertxUnitRunner::class)
class JsonRPCMounterTest {

  private val vertx = Vertx.vertx()

  private val service = ControlledService()
  private val executor = ConcreteServiceExecutor(service)
  private val socket = InvocableMockSocket()
  private val nonBlocking =
    NonBlockingSocket<JsonRPCRequest, JsonRPCResponse>(vertx).apply {
      socket.addListener(this)
    }

  init {
    JsonRPCMounter(executor, vertx).apply { nonBlocking.addListener(this) }
  }

  @After
  fun after() {
    socket.end()
  }

  @Test
  fun `that we can execute a simple request`(context: TestContext) {
    val result = socket.invoke(service::doSomething)
    context.assertEquals("result", result)
  }

  @Test
  fun `that we can execute something async`(context: TestContext) {
    val result = socket.invoke(service::doSomethingAsync).getOrThrow()
    context.assertEquals("result", result)
  }

  @Test(expected = JsonRPCException::class)
  fun `that we can execute something that throws`() {
    socket.invoke(service::fails)
  }

  @Test(expected = JsonRPCException::class)
  fun `that we can execute something async that throws`() {
    socket.invoke(service::failsAsync).getOrThrow()
  }

  @Test
  fun `that we can execute a stream`() {
    val result =
      socket.invoke(service::someStream).toList().map { it.toList() }.toFuture()
        .getOrThrow()
    assertEquals(listOf(1, 2, 3), result)
  }

  @Ignore("see issue #84")
  @Test
  fun `that executing a second invocation with the same id as an active invocation fails`() {
    val id = socket.nextId()
    socket.invoke(id, service::block) // request one will prepare the service and block
    service.waitForServiceReady()
    // the next invocation, if it sees the same id, then it should raise an error
    assertFailsWith<JsonRPCException> { socket.invoke(id, service::doSomething) }
  }

  @Test
  fun `that we cancel a stream`(context: TestContext) {
    val async = context.async()
    val subscription =
      socket.invoke(service::cancellableStream).subscribe(object : Subscriber<Int>() {
        override fun onNext(t: Int?) {
          if (!async.isCompleted) {
            async.complete()
          }
        }

        override fun onCompleted() {
          error("should never see this")
        }

        override fun onError(e: Throwable?) {
          error("should never see this")
        }
      })
    async.await()
    subscription.unsubscribe()
    service.waitForServiceReady()
  }

  @Test
  fun `that calling an unknown method fails`(context: TestContext) {
    val id = socket.nextId()
    try {
      socket.invoke<String>(id, "unknownMethod")
      throw RuntimeException("this should not be executed")
    } catch (err: JsonRPCException) {
      context.assertEquals(id, err.response.id)
      context.assertEquals(METHOD_NOT_FOUND, err.response.error.code)
    }
  }
}

class ControlledService {
  companion object {
    private val log = loggerFor<ControlledService>()
  }

  private val serviceReady = CountDownLatch(1)
  private val trigger = CountDownLatch(1)
  internal fun trigger() {
    trigger.countDown()
  }

  internal fun waitForServiceReady() {
    serviceReady.await()
  }

  private fun serviceReady() {
    serviceReady.countDown()
  }

  fun block(): Future<String> {
    log.trace("starting block()")
    val result = Future.future<String>()
    object : Thread() {
      override fun run() {
        serviceReady()
        log.trace("waiting for trigger ")
        trigger.await()
        log.trace("completing")
        result.complete("result")
      }
    }.start()
    return result
  }

  fun doSomething(): String {
    return "result"
  }

  fun fails(): String {
    throw RuntimeException("failed")
  }

  fun doSomethingAsync(): Future<String> {
    val result = Future.future<String>()
    object : Thread() {
      override fun run() {
        result.complete("result")
      }
    }.start()
    return result
  }

  fun failsAsync(): Future<String> {
    val result = Future.future<String>()
    object : Thread() {
      override fun run() {
        result.fail("failed")
      }
    }.start()
    return result
  }

  fun someStream(): Observable<Int> {
    return Observable.just(1, 2, 3).subscribeOn(Schedulers.computation())
  }

  fun cancellableStream(): Observable<Int> {
    @Suppress("DEPRECATION")
    return Observable.create<Int> {
      var next = 1
      while (!it.isUnsubscribed) {
        it.onNext(next++)
      }
    }
      .doOnUnsubscribe { serviceReady() }
      .onBackpressureBuffer()
      .subscribeOn(Schedulers.newThread())
  }
}
