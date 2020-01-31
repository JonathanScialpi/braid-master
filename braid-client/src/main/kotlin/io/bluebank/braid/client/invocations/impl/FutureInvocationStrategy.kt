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
import io.bluebank.braid.core.logging.loggerFor
import io.vertx.core.Future
import java.lang.reflect.Type

internal class FutureInvocationStrategy(
  parent: InvocationsInternal,
  method: String,
  returnType: Type,
  params: Array<out Any?>
) : InvocationStrategy<Future<Any?>>(parent, method, returnType, params) {

  companion object {
    private val log = loggerFor<FutureInvocationStrategy>()
  }

  private val result = Future.future<Any?>()
  private var requestId: Long = -1
  private var receivedCompletion = false

  override fun getResult(): Future<Any?> {
    try {
      requestId = nextRequestId()
      log.trace(requestId) { "invocation of $method initiated" }
      beginInvoke(requestId)
    } catch (err: Throwable) {
      log.error(requestId, err) { "failure in issuing invocation request" }
      endInvoke(requestId)
      result.fail(err)
    }
    return result
  }

  override fun onNext(requestId: Long, item: Any?) {
    log.trace(requestId) { "process onNext $item" }
    checkIdIsSet(requestId)
    endInvoke(requestId)
    check(!result.isComplete) { "future should not be completed" }
    result.complete(item)
  }

  override fun onError(requestId: Long, error: Throwable) {
    checkIdIsSet(requestId)
    endInvoke(requestId)
    check(!result.isComplete) { "future should not be completed" }
    result.fail(error)
  }

  override fun onCompleted(requestId: Long) {
    checkIdIsSet(requestId)
    endInvoke(requestId)
    check(result.isComplete) { "result should have been completed but didn't complete " }
    check(!receivedCompletion) { "completion message received before" }
    receivedCompletion = true
  }

  private fun checkIdIsSet(requestId: Long) {
    if (this.requestId < 0) error("computation appears not to have been invoked but I received a message for request $requestId")
    if (requestId != this.requestId) error("computation was invoked with request ${this.requestId} but I've received a message for request $requestId")
  }
}
