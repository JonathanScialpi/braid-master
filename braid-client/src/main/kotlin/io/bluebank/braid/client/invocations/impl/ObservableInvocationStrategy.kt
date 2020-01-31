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

import io.bluebank.braid.core.async.catch
import io.bluebank.braid.core.async.onSuccess
import io.bluebank.braid.core.jsonrpc.JsonRPCRequest
import io.bluebank.braid.core.jsonrpc.error
import io.bluebank.braid.core.jsonrpc.trace
import io.bluebank.braid.core.logging.loggerFor
import rx.Observable
import rx.Subscriber
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

internal class ObservableInvocationStrategy(
  parent: InvocationsInternal,
  method: String,
  returnType: Type,
  params: Array<out Any?>
) : InvocationStrategy<Observable<Any>>(parent, method, returnType, params) {

  companion object {
    private val log = loggerFor<ObservableInvocationStrategy>()
  }

  @Suppress("DEPRECATION")
  private val result: Observable<Any> = Observable.create<Any>(this::onSubscribe)
  private val subscribers = ConcurrentHashMap<Long, Subscriber<Any>>()

  override fun getResult() = result

  private fun beginInvoke(requestId: Long, subscriber: Subscriber<Any>) {
    subscribers[requestId] = subscriber
    beginInvoke(requestId)
  }

  override fun endInvoke(requestId: Long) {
    subscribers.remove(requestId)
    super.endInvoke(requestId)
  }

  private fun onSubscribe(subscriber: Subscriber<Any>) {
    val requestId = nextRequestId()
    log.trace(requestId) { "subscription for $method initiated" }
    beginInvoke(requestId, subscriber)
  }

  override fun onNext(requestId: Long, item: Any?) {
    log.trace(requestId) { "handling onNext for item $item" }

    checkMessageNotNull(requestId, item)
    val subscriber = getSubscriber(requestId)
    when {
      subscriber.isUnsubscribed -> nextOnUnsubscribed(requestId)
      else -> nextOnSubscribed(subscriber, item, requestId)
    }
  }

  override fun onError(requestId: Long, error: Throwable) {
    log.trace(requestId) { "handling onError" }
    val subscriber = getSubscriber(requestId)
    when {
      subscriber.isUnsubscribed -> errorOnUnsubscribed(requestId)
      else -> errorOnSubscribed(subscriber, error, requestId)
    }
  }

  override fun onCompleted(requestId: Long) {
    log.trace(requestId) { "handling onCompleted" }
    val subscriber = getSubscriber(requestId)
    when {
      subscriber.isUnsubscribed -> completeOnUnsubscribed(requestId)
      else -> completeOnSubscribed(subscriber, requestId)
    }
  }

  private fun nextOnSubscribed(subscriber: Subscriber<Any>, item: Any?, requestId: Long) {
    try {
      subscriber.onNext(item)
      if (subscriber.isUnsubscribed) {
        cancelStream(requestId)
      }
    } catch (err: Throwable) {
      log.error(
        requestId,
        err
      ) { "calling onNext failed because subscriber had an exception in both onNext and onError!" }
      cancelStream(requestId)
    }
  }

  private fun nextOnUnsubscribed(requestId: Long) {
    log.trace(requestId) { "subscriber has unsubscribed. sending the cancel stream payload" }
    cancelStream(requestId)
  }

  private fun cancelStream(requestId: Long) {
    try {
      sendStreamCancellation(requestId)
    } finally {
      endInvoke(requestId)
    }
  }

  private fun completeOnSubscribed(subscriber: Subscriber<Any>, requestId: Long) {
    endInvoke(requestId)
    subscriber.onCompleted()
  }

  private fun errorOnSubscribed(
    subscriber: Subscriber<Any>,
    error: Throwable,
    requestId: Long
  ) {
    endInvoke(requestId)
    subscriber.onError(error)
  }

  private fun completeOnUnsubscribed(requestId: Long) {
    try {
      log.trace(requestId) { "subscriber has unsubscribed. therefore, not sending the completion message to it" }
    } finally {
      endInvoke(requestId)
    }
  }

  private fun errorOnUnsubscribed(requestId: Long) {
    try {
      log.trace(requestId) { "subscriber has unsubscribed. therefore, not sending the error message to it" }
    } finally {
      endInvoke(requestId)
    }
  }

  internal val subscriberCount: Int get() = subscribers.size

  private fun getSubscriber(requestId: Long): Subscriber<Any> {
    return subscribers[requestId] ?: error(
      requestId,
      "failed to locate subscriber for request id $requestId"
    )
  }

  private fun error(requestId: Long, message: String): Nothing {
    log.error(requestId) { message }
    error(message)
  }

  private fun sendStreamCancellation(requestId: Long) {
    log.trace(requestId) { "sending stream cancellation" }
    send(JsonRPCRequest.cancelRequest(id = requestId))
      .onSuccess { log.trace(requestId) { "cancellation message sent" } }
      .catch {
        log.error(requestId) { "failed to send cancellation request for $requestId" }
      }
  }

  private fun checkMessageNotNull(requestId: Long, item: Any?) {
    if (item == null) {
      val msg = "received null item for an Observable"
      log.error(requestId) { msg }
      throw IllegalArgumentException(msg)
    }
  }
}
