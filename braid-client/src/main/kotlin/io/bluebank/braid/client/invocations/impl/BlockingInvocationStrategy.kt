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

import io.bluebank.braid.core.jsonrpc.error
import io.bluebank.braid.core.jsonrpc.trace
import io.bluebank.braid.core.jsonrpc.warn
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Future
import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch

internal class BlockingInvocationStrategy(
  parent: InvocationsInternal,
  method: String,
  returnType: Type,
  params: Array<out Any?>
) : InvocationStrategy<Any?>(parent, method, returnType, params) {

  private val result = Future.future<Any?>()
  private val latch = CountDownLatch(1)
  private var requestId = -1L

  companion object {
    private val log = loggerFor<BlockingInvocationStrategy>()
  }

  override fun getResult(): Any? {
    try {
      checkIdIsNotSet()
      requestId = nextRequestId()
      log.trace(requestId) { "preparing invocation" }
      beginInvoke(requestId)
      log.trace(requestId) { "awaiting result" }
      latch.await()
      log.trace(requestId) { "processing result" }
      return when {
        !result.isComplete -> error("I should have a result or error for you but but neither condition was met!")
        result.failed() -> throw result.cause()
        else -> result.result()
      }
    } catch (err: Throwable) {
      log.error(requestId, err) { err.message!! }
      throw err
    }
  }

  override fun onNext(requestId: Long, item: Any?) {
    log.trace(requestId) { "processing item $item" }
    checkIdIsSet(requestId)
    endInvoke(requestId)
    checkComputationIsNotComplete()
    result.complete(item)
    log.trace(requestId) { "signalling to the blocked client" }
    latch.countDown()
  }

  override fun onError(requestId: Long, error: Throwable) {
    log.trace(requestId) { "processing error ${error.message}" }
    endInvoke(requestId)
    checkIdIsSet(requestId)
    checkComputationIsNotComplete()
    result.fail(error)
    latch.countDown()
  }

  override fun onCompleted(requestId: Long) {
    log.warn(requestId) { "processing onCompleted message - unexpected for a blocking synchronous call" }
    checkIdIsSet(requestId)
    endInvoke(requestId)
    checkComputationIsComplete()
    // NO OP
  }

  private fun checkIdIsNotSet() {
    if (requestId >= 0) {
      val msg = "this request has already been started with requestId $requestId"
      log.error(requestId) { msg }
      error(msg)
    }
  }

  private fun checkIdIsSet(requestId: Long) {
    if (this.requestId < 0) error("computation appears not to have been invoked but I received a message for request $requestId")
    if (requestId != this.requestId) error("computation was invoked with request ${this.requestId} but I've received a message for request $requestId")
  }

  private fun checkComputationIsNotComplete() {
    if (result.isComplete) error("I received a message for request $requestId but computation is already complete!")
  }

  private fun checkComputationIsComplete() {
    if (!result.isComplete) error("I received a message for completion for request $requestId but I haven't received a result!")
  }
}
