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
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal abstract class InvocationsInternalImpl(
  private val destinationName: String = "",
  private val invocationTarget: InvocationTarget = InvocationStrategy.Companion::invoke
) : InvocationsInternal {

  companion object {
    private val log = loggerFor<InvocationsInternalImpl>()
  }

  private val nextRequestId = AtomicLong(0)
  private val invocations = ConcurrentHashMap<Long, InvocationStrategy<*>>()

  /**
   * generate the next request id
   * thread safe
   *
   * TODO: this method has an edge condition for when there are near to Long.MAX_VALUE number of active invocations
   */
  override fun nextRequestId(): Long {
    return nextRequestId.updateAndGet { current ->
      // sequence to get the next id, wrapping to 1 if we hit Long.MAX_VALUE
      generateSequence(Math.max(current + 1, 1)) { it + 1 }
        .filter {
          if (it == 0L) {
            // we've cycled around and couldn't find an available id!
            // are we sure we don't have an invocation leak?
            throw RuntimeException("failed to generate the next id because all are being actively used")
          }
          // skip the ones that are being used in active invocations
          !invocations.containsKey(it)
        }.first() // just the first one we find
    }
  }

  /**
   * Number of invocations in progress
   */
  override val activeRequestsCount: Int get() = invocations.size

  /**
   * set the invocation [strategy] for a [requestId]
   */
  override fun setStrategy(requestId: Long, strategy: InvocationStrategy<*>) {
    if (invocations.containsKey(requestId)) {
      val msg = "tried to add a strategy for request $requestId but one already exists!"
      log.error(requestId) { msg }
      error(msg)
    } else {
      log.trace("adding strategy for request $requestId")
      invocations[requestId] = strategy
    }
  }

  /**
   * unset / remove the invocation strategy assigned to [requestId]
   */
  override fun removeStrategy(requestId: Long) {
    if (invocations.containsKey(requestId)) {
      log.trace(requestId) { "removing strategy for request" }
      invocations.remove(requestId)
    } else {
      log.error(requestId) { "could not remove strategy for request $requestId because none could be found!" }
    }
  }

  /**
   * public entry point to invoke a method. may block depending if the call has synchronous signature
   * may not invoke anything at all if the call returns an [rx.Observable]
   * @param [method] the name of the method
   * @param [returnType] the expected return type of the function being called
   * @param [params] the parameters for the call
   * @return the result of the invocation
   */
  override fun invoke(method: String, returnType: Type, params: Array<out Any?>): Any? {
    return invocationTarget(this, method, returnType, params)
  }

  override fun close() {
    log.info("closing with $activeRequestsCount invocations in progress")
  }

  private fun isInvocationLive(requestId: Long) = invocations.containsKey(requestId)
  fun getInvocationStrategy(requestId: Long) = invocations[requestId]

  /**
   * direct callback from the socket when there's a new [textMessage] available
   * the response is validated
   * if it has an requestId with an assigned strategy, it's dispatched for processing by the respective [InvocationStrategy]
   * otherwise it is logged as an error
   */
  protected fun receive(buffer: Buffer) {
    try {
      val jo = JsonObject(buffer)
      if (!jo.containsKey("id")) {
        log.warn("received message without 'id' field from $destinationName")
        return
      }
      val requestId = jo.getLong("id")
      when {
        requestId == null -> log.error(
          "received response without id {}",
          buffer.toString()
        )
        !isInvocationLive(requestId) -> log.error(requestId) { "no subscriber found for response id $requestId" }
        else -> receive(requestId, jo)
      }
    } catch (err: Throwable) {
      log.error("failed to handle response message $buffer", err)
    }
  }

  /**
   * given a [payload] for a [requestId] finds the respective [InvocationStrategy] for dispatch
   * otherwise, logs error and continues
   */
  private fun receive(requestId: Long, payload: JsonObject) {
    log.trace(requestId) { "handling response ${Json.encode(payload)}" }
    try {
      getInvocationStrategy(requestId)?.handlePayload(requestId, payload)
        ?: log.error(requestId) { "no subscriber found for request id $requestId" }
    } catch (err: Throwable) {
      log.error(requestId, err) { "failed to handle response message" }
    }
  }
}